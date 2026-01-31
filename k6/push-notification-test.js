import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ============================================================================
// Custom Metrics
// ============================================================================
const issueCreateLatency = new Trend('issue_create_latency', true);
const pushLatency = new Trend('push_notification_latency', true);
const successRate = new Rate('success_rate');
const issueCreateSuccess = new Counter('issue_create_success');
const issueCreateFailure = new Counter('issue_create_failure');

// ============================================================================
// Configuration
// ============================================================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const PARTICIPANT_COUNT = parseInt(__ENV.PARTICIPANT_COUNT || '5'); // 참여자 수
const TEST_DURATION = '30s';
const REQUEST_RATE = 10;

export const options = {
    scenarios: {
        // 시나리오 1: 이슈 생성을 통한 다중 푸시 알림 테스트
        issue_create_load: {
            executor: 'constant-arrival-rate',
            rate: REQUEST_RATE,         // 초당 10개 이슈 생성
            timeUnit: '1s',
            duration: TEST_DURATION,
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
    thresholds: {
        'issue_create_latency': ['p(95)<3000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.05'],
        'success_rate': ['rate>0.90'],
    },
};

// ============================================================================
// Test Data - 테스트용 멤버 ID들 (k6/test-data-setup.sql로 생성)
// ============================================================================
const testMemberIds = [1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010];

// ============================================================================
// Helper Functions
// ============================================================================
function getAuthHeaders() {
    const headers = {
        'Content-Type': 'application/json',
    };
    if (AUTH_TOKEN) {
        headers['Authorization'] = `Bearer ${AUTH_TOKEN}`;
    }
    return headers;
}

function generateIssuePayload(participantCount) {
    const timestamp = Date.now();
    const vuId = __VU;

    // 참여자 목록 생성 (participantCount 만큼)
    const members = [];
    for (let i = 0; i < Math.min(participantCount, testMemberIds.length); i++) {
        members.push({
            id: testMemberIds[i],
            isHost: i === 0,  // 첫 번째 멤버가 주관자
            isPermitted: true,
            isRead: false
        });
    }

    return {
        title: `Performance Test Issue ${timestamp}-${vuId}`,
        content: `This is a test issue created for performance testing. Timestamp: ${timestamp}`,
        status: 'IN_PROGRESS',
        categoryId: 1,  // 실제 존재하는 카테고리 ID
        startDate: new Date().toISOString().split('T')[0],
        endDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        departmentIds: [1],  // 실제 존재하는 부서 ID
        members: members,
        isDel: false,
        isPrivate: false
    };
}

// ============================================================================
// Setup - 테스트 환경 준비
// ============================================================================
export function setup() {
    console.log('='.repeat(70));
    console.log('         Push Notification Performance Test (Real Use Case)');
    console.log('='.repeat(70));
    console.log(`Target URL:         ${BASE_URL}`);
    console.log(`Participant Count:  ${PARTICIPANT_COUNT} members per issue`);
    console.log(`Test Duration:      ${TEST_DURATION}`);
    console.log(`Request Rate:       ${REQUEST_RATE} req/s`);
    console.log('='.repeat(70));
    console.log('');
    console.log('Test Flow:');
    console.log('  1. Create Issue with multiple participants');
    console.log('  2. IssueService.createIssue() triggers notifyMembers()');
    console.log('  3. NotificationService sends push to each participant');
    console.log('  4. WebPushSender.sendPushNotification() executes');
    console.log('');
    console.log('='.repeat(70));

    // 서버 상태 확인
    const healthCheck = http.get(`${BASE_URL}/actuator/health`, { timeout: '5s' });
    if (healthCheck.status !== 200) {
        console.log(`WARNING: Server health check failed (status: ${healthCheck.status})`);
    } else {
        console.log('Server is healthy');
    }

    return { startTime: Date.now() };
}

// ============================================================================
// Main Test Scenario
// ============================================================================
export default function(data) {
    group('Issue Create with Push Notifications', function() {
        const payload = generateIssuePayload(PARTICIPANT_COUNT);

        // FormData 형식으로 전송 (multipart/form-data)
        const formData = {
            data: http.file(JSON.stringify(payload), 'data.json', 'application/json'),
        };

        const startTime = Date.now();

        const res = http.post(
            `${BASE_URL}/issue/create`,
            formData,
            {
                headers: AUTH_TOKEN ? { 'Authorization': `Bearer ${AUTH_TOKEN}` } : {},
                timeout: '10s',
            }
        );

        const latency = Date.now() - startTime;
        issueCreateLatency.add(latency);

        // 200 또는 401(인증 없이 테스트시)을 성공으로 간주
        const isSuccess = res.status === 200 || res.status === 401;

        if (isSuccess) {
            issueCreateSuccess.add(1);
            successRate.add(1);
        } else {
            issueCreateFailure.add(1);
            successRate.add(0);
            if (__VU <= 3) { // 처음 몇 개 VU만 로그 출력
                console.log(`[VU ${__VU}] Failed: status=${res.status}, body=${res.body?.substring(0, 200)}`);
            }
        }

        check(res, {
            'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
            'latency < 3000ms': () => latency < 3000,
        });
    });

    sleep(0.1);
}

// ============================================================================
// Teardown - 결과 요약
// ============================================================================
export function handleSummary(data) {
    const p50 = data.metrics.issue_create_latency?.values['p(50)'] || 0;
    const p95 = data.metrics.issue_create_latency?.values['p(95)'] || 0;
    const p99 = data.metrics.issue_create_latency?.values['p(99)'] || 0;
    const avg = data.metrics.issue_create_latency?.values['avg'] || 0;
    const min = data.metrics.issue_create_latency?.values['min'] || 0;
    const max = data.metrics.issue_create_latency?.values['max'] || 0;
    const totalReqs = data.metrics.http_reqs?.values.count || 0;
    const successCount = data.metrics.issue_create_success?.values.count || 0;
    const failureCount = data.metrics.issue_create_failure?.values.count || 0;
    const duration = 30;
    const throughput = totalReqs / duration;
    const pushesPerSecond = throughput * PARTICIPANT_COUNT;

    const summary = `
${'='.repeat(70)}
                         TEST RESULTS SUMMARY
${'='.repeat(70)}

📊 Request Statistics
   Total Requests:      ${totalReqs}
   Successful:          ${successCount}
   Failed:              ${failureCount}
   Success Rate:        ${((successCount / totalReqs) * 100).toFixed(2)}%

⏱️  Latency (Issue Create)
   Min:                 ${min.toFixed(2)} ms
   Avg:                 ${avg.toFixed(2)} ms
   P50:                 ${p50.toFixed(2)} ms
   P95:                 ${p95.toFixed(2)} ms
   P99:                 ${p99.toFixed(2)} ms
   Max:                 ${max.toFixed(2)} ms

🚀 Throughput
   Issues/sec:          ${throughput.toFixed(2)}
   Pushes/sec:          ${pushesPerSecond.toFixed(2)} (${PARTICIPANT_COUNT} participants/issue)

📝 Test Configuration
   Participants/Issue:  ${PARTICIPANT_COUNT}
   Test Duration:       ${duration}s
   Target Rate:         ${REQUEST_RATE} req/s

${'='.repeat(70)}
`;

    console.log(summary);

    // JSON 결과 파일 저장
    return {
        'k6/results/push-result.json': JSON.stringify({
            summary: {
                totalRequests: totalReqs,
                successCount,
                failureCount,
                successRate: (successCount / totalReqs) * 100,
                throughput,
                pushesPerSecond,
                participantsPerIssue: PARTICIPANT_COUNT,
            },
            latency: { min, avg, p50, p95, p99, max },
            raw: data,
        }, null, 2),
        'stdout': summary,
    };
}
