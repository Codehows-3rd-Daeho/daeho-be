import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration', true);
const issueDuration = new Trend('issue_api_duration', true);
const meetingDuration = new Trend('meeting_api_duration', true);

// ─── 설정 ───
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';
const ADMIN_ID = __ENV.ADMIN_ID || 'admin';
const ADMIN_PW = __ENV.ADMIN_PW || 'admin1234';

// ─── 부하 시나리오 ───
export const options = {
    scenarios: {
        // 1) 스모크 테스트: 기본 동작 확인
        smoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 10,
            startTime: '0s',
            tags: { test_type: 'smoke' },
        },
        // 2) 평균 부하: 일반 사용 패턴
        average_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },  // 30초간 20명까지 증가
                { duration: '1m', target: 20 },    // 1분간 20명 유지
                { duration: '30s', target: 0 },    // 30초간 0명으로 감소
            ],
            startTime: '30s',
            tags: { test_type: 'average' },
        },
        // 3) 스트레스 테스트: 한계 확인
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m', target: 50 },
                { duration: '30s', target: 100 },
                { duration: '1m', target: 100 },
                { duration: '30s', target: 0 },
            ],
            startTime: '3m',
            tags: { test_type: 'stress' },
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],      // 95% 요청이 2초 이내
        http_req_failed: ['rate<0.05'],          // 실패율 5% 미만
        errors: ['rate<0.1'],                    // 에러율 10% 미만
        login_duration: ['p(95)<3000'],          // 로그인 95%가 3초 이내
        issue_api_duration: ['p(95)<2000'],      // 이슈 API 95%가 2초 이내
        meeting_api_duration: ['p(95)<2000'],    // 회의 API 95%가 2초 이내
    },
};

// ─── 헬퍼 함수 ───
function headers(token) {
    return { headers: { Authorization: token, 'Content-Type': 'application/json' } };
}

function login(loginId, password) {
    const res = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ loginId, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    loginDuration.add(res.timings.duration);

    if (check(res, { 'login 200': (r) => r.status === 200 })) {
        const body = JSON.parse(res.body);
        return body.token;
    }
    errorRate.add(1);
    return null;
}

// ─── 메인 시나리오 ───
export default function () {
    // 1. 로그인
    const token = login(LOGIN_ID, PASSWORD);
    if (!token) {
        errorRate.add(1);
        return;
    }

    // 2. 이슈 API
    group('이슈 API', () => {
        // 칸반 조회
        const kanban = http.get(`${BASE_URL}/issue/kanban?memberId=1`, headers(token));
        issueDuration.add(kanban.timings.duration);
        check(kanban, { '칸반 조회 200': (r) => r.status === 200 });
        errorRate.add(kanban.status !== 200);

        // 이슈 리스트 조회
        const list = http.get(`${BASE_URL}/issue/list?memberId=1&page=0&size=10`, headers(token));
        issueDuration.add(list.timings.duration);
        check(list, { '이슈 리스트 200': (r) => r.status === 200 });
        errorRate.add(list.status !== 200);

        sleep(0.5);
    });

    // 3. 회의 API
    group('회의 API', () => {
        // 회의 리스트 조회
        const list = http.get(`${BASE_URL}/meeting/list?memberId=1&page=0&size=10`, headers(token));
        meetingDuration.add(list.timings.duration);
        check(list, { '회의 리스트 200': (r) => r.status === 200 });
        errorRate.add(list.status !== 200);

        // 캘린더 조회
        const now = new Date();
        const scheduler = http.get(
            `${BASE_URL}/meeting/scheduler?memberId=1&year=${now.getFullYear()}&month=${now.getMonth() + 1}`,
            headers(token)
        );
        meetingDuration.add(scheduler.timings.duration);
        check(scheduler, { '캘린더 조회 200': (r) => r.status === 200 });
        errorRate.add(scheduler.status !== 200);

        sleep(0.5);
    });

    // 4. 알림 API
    group('알림 API', () => {
        const notif = http.get(`${BASE_URL}/notifications?page=0&size=5`, headers(token));
        check(notif, { '알림 조회 200': (r) => r.status === 200 });
        errorRate.add(notif.status !== 200);

        const unread = http.get(`${BASE_URL}/notifications/unread-count`, headers(token));
        check(unread, { '안읽은 알림 200': (r) => r.status === 200 });
        errorRate.add(unread.status !== 200);

        sleep(0.3);
    });

    // 5. 기준 데이터 API
    group('기준 데이터 API', () => {
        const categories = http.get(`${BASE_URL}/masterData/category`, headers(token));
        check(categories, { '카테고리 200': (r) => r.status === 200 });

        const departments = http.get(`${BASE_URL}/masterData/department`, headers(token));
        check(departments, { '부서 200': (r) => r.status === 200 });

        const positions = http.get(`${BASE_URL}/masterData/jobPosition`, headers(token));
        check(positions, { '직급 200': (r) => r.status === 200 });

        sleep(0.3);
    });

    sleep(1);
}
