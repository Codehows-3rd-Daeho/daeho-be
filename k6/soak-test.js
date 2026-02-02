import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';

// ─── 내구성 테스트: 장시간 안정성 확인 (메모리 누수 등) ───
export const options = {
    scenarios: {
        soak: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 30 },    // 증가
                { duration: '10m', target: 30 },   // 장시간 유지
                { duration: '1m', target: 0 },     // 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.05'],
    },
};

function headers(token) {
    return { headers: { Authorization: token, 'Content-Type': 'application/json' } };
}

export default function () {
    const loginRes = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ loginId: LOGIN_ID, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginRes.status !== 200) {
        errorRate.add(1);
        sleep(2);
        return;
    }

    const token = JSON.parse(loginRes.body).token;

    // 일반 사용자 패턴 시뮬레이션
    const issueList = http.get(`${BASE_URL}/issue/list?memberId=1&page=0&size=10`, headers(token));
    check(issueList, { '이슈 리스트 200': (r) => r.status === 200 });

    sleep(1);

    const meetingList = http.get(`${BASE_URL}/meeting/list?memberId=1&page=0&size=10`, headers(token));
    check(meetingList, { '회의 리스트 200': (r) => r.status === 200 });

    sleep(1);

    const notif = http.get(`${BASE_URL}/notifications?page=0&size=5`, headers(token));
    check(notif, { '알림 200': (r) => r.status === 200 });

    sleep(2);
}
