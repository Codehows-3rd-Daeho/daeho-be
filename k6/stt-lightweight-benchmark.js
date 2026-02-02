import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';
import { randomBytes } from 'k6/crypto';

/**
 * STT 경량화 아키텍처 성능 벤치마크
 *
 * 측정 지표:
 * - TPS (Transactions Per Second)
 * - Latency (p50, p90, p95, p99)
 * - Throughput
 * - Error Rate
 *
 * 테스트 대상:
 * 1. 녹음 시작 API
 * 2. 청크 업로드 API (Heartbeat 갱신 포함)
 * 3. 상태 조회 API (캐시 히트)
 * 4. @Async 인코딩 완료 대기
 * 5. @Scheduled 폴링 처리 시간
 */

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('error_rate');

// API 레이턴시
const startRecordingLatency = new Trend('start_recording_latency', true);
const chunkUploadLatency = new Trend('chunk_upload_latency', true);
const statusQueryLatency = new Trend('status_query_latency', true);
const finishRecordingLatency = new Trend('finish_recording_latency', true);
const translateStartLatency = new Trend('translate_start_latency', true);

// 처리 시간
const encodingDuration = new Trend('encoding_duration', true);
const pollingCycleDuration = new Trend('polling_cycle_duration', true);
const totalSessionDuration = new Trend('total_session_duration', true);

// 카운터
const successfulSessions = new Counter('successful_sessions');
const failedSessions = new Counter('failed_sessions');
const totalChunksUploaded = new Counter('total_chunks_uploaded');

// 게이지 (현재 상태)
const activeRecordings = new Gauge('active_recordings');

// ─── 설정 ───
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';
const MEETING_ID = __ENV.MEETING_ID || '1';

// ─── 부하 시나리오 ───
export const options = {
    scenarios: {
        // 1) 단일 세션 벤치마크 (베이스라인 측정)
        baseline: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 5,
            startTime: '0s',
            tags: { test_type: 'baseline' },
        },
        // 2) 동시 세션 부하 테스트 (10 VU)
        concurrent_load: {
            executor: 'constant-vus',
            vus: 10,
            duration: '1m',
            startTime: '30s',
            tags: { test_type: 'concurrent_load' },
        },
        // 3) 램프업 스트레스 테스트
        ramp_stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 10 },
                { duration: '30s', target: 20 },
                { duration: '20s', target: 30 },
                { duration: '30s', target: 30 },
                { duration: '20s', target: 0 },
            ],
            startTime: '2m',
            tags: { test_type: 'ramp_stress' },
        },
        // 4) 상태 조회 집중 테스트 (캐시 성능)
        cache_benchmark: {
            executor: 'constant-arrival-rate',
            rate: 100, // 초당 100 요청
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 20,
            maxVUs: 50,
            startTime: '5m',
            exec: 'statusQueryOnly',
            tags: { test_type: 'cache_benchmark' },
        },
    },
    thresholds: {
        // API 레이턴시 임계값
        start_recording_latency: ['p(95)<500', 'p(99)<1000'],
        chunk_upload_latency: ['p(95)<300', 'p(99)<500'],
        status_query_latency: ['p(95)<100', 'p(99)<200'],  // 캐시 히트 기준
        finish_recording_latency: ['p(95)<500', 'p(99)<1000'],

        // 처리 시간 임계값
        encoding_duration: ['p(95)<10000'],  // 인코딩 10초 이내
        total_session_duration: ['p(95)<30000'],  // 전체 세션 30초 이내

        // 일반 메트릭
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.05'],
        error_rate: ['rate<0.05'],
    },
};

// ─── 헬퍼 함수 ───
function headers(token) {
    return {
        headers: {
            Authorization: token,
            'Content-Type': 'application/json',
        },
    };
}

function login(loginId, password) {
    const res = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ loginId, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (check(res, { 'login 성공': (r) => r.status === 200 })) {
        const body = JSON.parse(res.body);
        return body.token;
    }
    errorRate.add(1);
    return null;
}

function generateAudioChunk(sizeBytes = 8192) {
    const header = new Uint8Array([
        0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
        0x57, 0x41, 0x56, 0x45, 0x66, 0x6D, 0x74, 0x20,
        0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00,
        0x80, 0x3E, 0x00, 0x00, 0x00, 0x7D, 0x00, 0x00,
        0x02, 0x00, 0x10, 0x00, 0x64, 0x61, 0x74, 0x61,
        0x00, 0x00, 0x00, 0x00,
    ]);
    const audioData = randomBytes(sizeBytes - 44);
    const chunk = new Uint8Array(sizeBytes);
    chunk.set(header, 0);
    chunk.set(new Uint8Array(audioData), 44);
    return chunk;
}

// ─── 메인 시나리오: 전체 녹음 플로우 ───
export default function () {
    const sessionStart = Date.now();
    let token = login(LOGIN_ID, PASSWORD);

    if (!token) {
        failedSessions.add(1);
        return;
    }

    let sttId = null;

    group('1. 녹음 시작', () => {
        activeRecordings.add(1);
        const startTime = Date.now();

        const res = http.post(
            `${BASE_URL}/stt/recording/start`,
            JSON.stringify({ meetingId: parseInt(MEETING_ID) }),
            headers(token)
        );

        startRecordingLatency.add(res.timings.duration);

        const ok = check(res, {
            '녹음 시작 200': (r) => r.status === 200,
            '응답에 id 포함': (r) => JSON.parse(r.body).id !== undefined,
            '상태가 RECORDING': (r) => JSON.parse(r.body).status === 'RECORDING',
        });

        if (ok) {
            sttId = JSON.parse(res.body).id;
        } else {
            errorRate.add(1);
        }
    });

    if (!sttId) {
        failedSessions.add(1);
        activeRecordings.add(-1);
        return;
    }

    group('2. 청크 업로드 (5회)', () => {
        const chunkCount = 5;

        for (let i = 0; i < chunkCount; i++) {
            const isLast = i === chunkCount - 1;
            const chunk = generateAudioChunk(8192);

            const fd = new FormData();
            fd.append('file', http.file(chunk, `chunk-${i}.wav`, 'audio/wav'));
            if (isLast) {
                fd.append('finish', 'true');
            }

            const res = http.post(
                `${BASE_URL}/stt/${sttId}/chunk`,
                fd.body(),
                {
                    headers: {
                        Authorization: token,
                        'Content-Type': `multipart/form-data; boundary=${fd.boundary}`,
                    },
                }
            );

            chunkUploadLatency.add(res.timings.duration);
            totalChunksUploaded.add(1);

            const ok = check(res, { [`청크 ${i + 1} 업로드 성공`]: (r) => r.status === 200 });
            if (!ok) errorRate.add(1);

            sleep(0.2); // 청크 간 200ms 딜레이
        }
    });

    group('3. 인코딩 완료 대기 (@Async)', () => {
        const encodingStart = Date.now();
        let encodingComplete = false;
        const maxPolls = 30;

        for (let i = 0; i < maxPolls && !encodingComplete; i++) {
            const res = http.get(
                `${BASE_URL}/stt/status/${sttId}`,
                headers(token)
            );

            statusQueryLatency.add(res.timings.duration);

            if (res.status === 200) {
                const body = JSON.parse(res.body);
                if (body.status === 'ENCODED' || body.status === 'PROCESSING' ||
                    body.status === 'SUMMARIZING' || body.status === 'COMPLETED') {
                    encodingComplete = true;
                }
            }

            if (!encodingComplete) {
                sleep(0.5); // 500ms 간격 폴링
            }
        }

        const encodingEnd = Date.now();
        encodingDuration.add(encodingEnd - encodingStart);

        check(encodingComplete, { '인코딩 완료': (c) => c === true });
        if (!encodingComplete) errorRate.add(1);
    });

    // 세션 종료
    activeRecordings.add(-1);
    const sessionEnd = Date.now();
    totalSessionDuration.add(sessionEnd - sessionStart);

    if (sessionEnd - sessionStart < 30000) {
        successfulSessions.add(1);
    } else {
        failedSessions.add(1);
    }

    sleep(0.5);
}

// ─── 상태 조회 전용 시나리오 (캐시 벤치마크) ───
export function statusQueryOnly() {
    const token = login(LOGIN_ID, PASSWORD);
    if (!token) return;

    // 존재하는 STT ID로 상태 조회 (캐시 히트 테스트)
    const sttId = 1; // 테스트용 고정 ID

    const res = http.get(
        `${BASE_URL}/stt/status/${sttId}`,
        headers(token)
    );

    statusQueryLatency.add(res.timings.duration);

    check(res, {
        '상태 조회 200 또는 404': (r) => r.status === 200 || r.status === 404,
    });
}

// ─── 라이프사이클 훅 ───
export function setup() {
    console.log('━'.repeat(60));
    console.log('  STT 경량화 아키텍처 성능 벤치마크');
    console.log('━'.repeat(60));
    console.log(`  Base URL: ${BASE_URL}`);
    console.log(`  Meeting ID: ${MEETING_ID}`);
    console.log('');
    console.log('  측정 항목:');
    console.log('  - 녹음 시작 API 레이턴시');
    console.log('  - 청크 업로드 레이턴시 (Heartbeat 포함)');
    console.log('  - 상태 조회 레이턴시 (Redis 캐시)');
    console.log('  - @Async 인코딩 완료 시간');
    console.log('  - 전체 세션 처리 시간');
    console.log('━'.repeat(60));

    // 헬스체크
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '5s' });
    if (health.status !== 200) {
        console.warn('⚠️ 서버 헬스체크 실패');
    }

    return { startTime: Date.now() };
}

export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log('━'.repeat(60));
    console.log(`  테스트 완료 - 총 소요 시간: ${duration.toFixed(1)}초`);
    console.log('━'.repeat(60));
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        duration_seconds: data.state.testRunDurationMs / 1000,
        metrics: {
            // API 레이턴시
            start_recording: {
                p50: data.metrics.start_recording_latency?.values?.['p(50)'] || 0,
                p95: data.metrics.start_recording_latency?.values?.['p(95)'] || 0,
                p99: data.metrics.start_recording_latency?.values?.['p(99)'] || 0,
            },
            chunk_upload: {
                p50: data.metrics.chunk_upload_latency?.values?.['p(50)'] || 0,
                p95: data.metrics.chunk_upload_latency?.values?.['p(95)'] || 0,
                p99: data.metrics.chunk_upload_latency?.values?.['p(99)'] || 0,
            },
            status_query: {
                p50: data.metrics.status_query_latency?.values?.['p(50)'] || 0,
                p95: data.metrics.status_query_latency?.values?.['p(95)'] || 0,
                p99: data.metrics.status_query_latency?.values?.['p(99)'] || 0,
            },
            encoding_duration: {
                p50: data.metrics.encoding_duration?.values?.['p(50)'] || 0,
                p95: data.metrics.encoding_duration?.values?.['p(95)'] || 0,
                p99: data.metrics.encoding_duration?.values?.['p(99)'] || 0,
            },
            // 처리량
            http_reqs: data.metrics.http_reqs?.values?.rate || 0,
            successful_sessions: data.metrics.successful_sessions?.values?.count || 0,
            failed_sessions: data.metrics.failed_sessions?.values?.count || 0,
            total_chunks: data.metrics.total_chunks_uploaded?.values?.count || 0,
            // 에러율
            error_rate: data.metrics.error_rate?.values?.rate || 0,
        },
        thresholds_passed: Object.entries(data.metrics)
            .filter(([k, v]) => v.thresholds)
            .every(([k, v]) => Object.values(v.thresholds).every(t => t.ok)),
    };

    return {
        'k6/stt-benchmark-results.json': JSON.stringify(summary, null, 2),
        stdout: generateTextSummary(summary),
    };
}

function generateTextSummary(summary) {
    return `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                 STT 경량화 아키텍처 성능 결과
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

▶ API 레이턴시 (ms)
  ┌─────────────────────┬─────────┬─────────┬─────────┐
  │ 항목                │   p50   │   p95   │   p99   │
  ├─────────────────────┼─────────┼─────────┼─────────┤
  │ 녹음 시작           │ ${summary.metrics.start_recording.p50.toFixed(1).padStart(7)} │ ${summary.metrics.start_recording.p95.toFixed(1).padStart(7)} │ ${summary.metrics.start_recording.p99.toFixed(1).padStart(7)} │
  │ 청크 업로드         │ ${summary.metrics.chunk_upload.p50.toFixed(1).padStart(7)} │ ${summary.metrics.chunk_upload.p95.toFixed(1).padStart(7)} │ ${summary.metrics.chunk_upload.p99.toFixed(1).padStart(7)} │
  │ 상태 조회 (캐시)    │ ${summary.metrics.status_query.p50.toFixed(1).padStart(7)} │ ${summary.metrics.status_query.p95.toFixed(1).padStart(7)} │ ${summary.metrics.status_query.p99.toFixed(1).padStart(7)} │
  │ 인코딩 완료         │ ${summary.metrics.encoding_duration.p50.toFixed(1).padStart(7)} │ ${summary.metrics.encoding_duration.p95.toFixed(1).padStart(7)} │ ${summary.metrics.encoding_duration.p99.toFixed(1).padStart(7)} │
  └─────────────────────┴─────────┴─────────┴─────────┘

▶ 처리량
  • HTTP 요청: ${summary.metrics.http_reqs.toFixed(1)}/s
  • 성공 세션: ${summary.metrics.successful_sessions}
  • 실패 세션: ${summary.metrics.failed_sessions}
  • 총 청크: ${summary.metrics.total_chunks}

▶ 에러율: ${(summary.metrics.error_rate * 100).toFixed(2)}%

▶ 임계값 통과: ${summary.thresholds_passed ? '✅ 모두 통과' : '❌ 일부 실패'}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;
}
