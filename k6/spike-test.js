import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';

// ─── 스파이크 테스트: 갑작스러운 트래픽 급증 ───
export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 5 },    // 준비
                { duration: '5s', target: 200 },    // 급증
                { duration: '30s', target: 200 },   // 유지
                { duration: '10s', target: 5 },     // 회복
                { duration: '30s', target: 5 },     // 안정화 확인
                { duration: '5s', target: 0 },      // 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],   // 스파이크에서는 5초까지 허용
        http_req_failed: ['rate<0.15'],      // 15% 미만 실패 허용
    },
};

function headers(token) {
    return { headers: { Authorization: token, 'Content-Type': 'application/json' } };
}

export default function () {
    // 로그인
    const loginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ loginId: LOGIN_ID, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginRes.status !== 200) {
        errorRate.add(1);
        sleep(1);
        return;
    }

    const token = JSON.parse(loginRes.body).token;

    // 가장 많이 호출되는 읽기 API들
    const responses = http.batch([
        ['GET', `${BASE_URL}/issue/kanban?memberId=1`, null, headers(token)],
        ['GET', `${BASE_URL}/meeting/list?memberId=1&page=0&size=10`, null, headers(token)],
        ['GET', `${BASE_URL}/notifications/unread-count`, null, headers(token)],
        ['GET', `${BASE_URL}/masterData/category`, null, headers(token)],
    ]);

    responses.forEach((res, i) => {
        check(res, { [`batch[${i}] 200`]: (r) => r.status === 200 });
        errorRate.add(res.status !== 200);
    });

    sleep(0.5);
}
