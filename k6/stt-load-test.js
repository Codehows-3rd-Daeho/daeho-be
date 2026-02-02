import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';
import { randomBytes } from 'k6/crypto';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const sttStartDuration = new Trend('stt_start_duration', true);
const sttChunkDuration = new Trend('stt_chunk_duration', true);
const sttStatusDuration = new Trend('stt_status_duration', true);
const sttFinishDuration = new Trend('stt_finish_duration', true);
const sttTotalDuration = new Trend('stt_total_duration', true);
const successfulSessions = new Counter('successful_stt_sessions');

// ─── 설정 ───
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';
const MEETING_ID = __ENV.MEETING_ID || '1';

// ─── 부하 시나리오 ───
export const options = {
    scenarios: {
        // 1) 스모크 테스트: 기본 플로우 확인
        smoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 3,
            startTime: '0s',
            tags: { test_type: 'smoke' },
        },
        // 2) 부하 테스트: 동시 10개 녹음 세션
        load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },   // 30초간 10 VU로 증가
                { duration: '2m', target: 10 },    // 2분간 10 VU 유지
                { duration: '30s', target: 0 },    // 30초간 0으로 감소
            ],
            startTime: '30s',
            tags: { test_type: 'load' },
        },
        // 3) 스트레스 테스트: 한계 성능 확인
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 30 },   // 30초간 30 VU로 증가
                { duration: '1m', target: 30 },    // 1분간 유지
                { duration: '30s', target: 50 },   // 30초간 50 VU로 증가
                { duration: '1m', target: 50 },    // 1분간 유지
                { duration: '30s', target: 0 },    // 30초간 0으로 감소
            ],
            startTime: '4m',
            tags: { test_type: 'stress' },
        },
    },
    thresholds: {
        // STT API 성능 임계값
        stt_start_duration: ['p(95)<1000'],     // 녹음 시작: p95 < 1초
        stt_chunk_duration: ['p(95)<500'],      // 청크 업로드: p95 < 500ms
        stt_status_duration: ['p(95)<300'],     // 상태 조회: p95 < 300ms
        stt_finish_duration: ['p(95)<2000'],    // 녹음 종료: p95 < 2초

        // 일반 HTTP 메트릭
        http_req_duration: ['p(95)<2000'],      // 전체 요청: p95 < 2초
        http_req_failed: ['rate<0.05'],          // 실패율 < 5%
        errors: ['rate<0.05'],                   // 에러율 < 5%
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

    if (check(res, { 'login 200': (r) => r.status === 200 })) {
        const body = JSON.parse(res.body);
        return body.token;
    }
    errorRate.add(1);
    return null;
}

// 테스트용 오디오 청크 생성 (WAV 형식 헤더 포함)
function generateAudioChunk(sizeBytes = 4096) {
    // WAV 파일 헤더 (44바이트) + 랜덤 데이터
    const header = new Uint8Array([
        0x52, 0x49, 0x46, 0x46, // "RIFF"
        0x00, 0x00, 0x00, 0x00, // 파일 크기 (플레이스홀더)
        0x57, 0x41, 0x56, 0x45, // "WAVE"
        0x66, 0x6D, 0x74, 0x20, // "fmt "
        0x10, 0x00, 0x00, 0x00, // fmt 청크 크기 (16)
        0x01, 0x00,             // 오디오 포맷 (1 = PCM)
        0x01, 0x00,             // 채널 수 (1 = 모노)
        0x80, 0x3E, 0x00, 0x00, // 샘플레이트 (16000)
        0x00, 0x7D, 0x00, 0x00, // 바이트레이트 (32000)
        0x02, 0x00,             // 블록 정렬 (2)
        0x10, 0x00,             // 비트 뎁스 (16)
        0x64, 0x61, 0x74, 0x61, // "data"
        0x00, 0x00, 0x00, 0x00, // 데이터 크기 (플레이스홀더)
    ]);

    // 랜덤 오디오 데이터 생성
    const audioData = randomBytes(sizeBytes - 44);
    const chunk = new Uint8Array(sizeBytes);
    chunk.set(header, 0);
    chunk.set(new Uint8Array(audioData), 44);

    return chunk;
}

// ─── 메인 시나리오 ───
export default function () {
    const sessionStart = Date.now();

    // 1. 로그인
    const token = login(LOGIN_ID, PASSWORD);
    if (!token) {
        errorRate.add(1);
        console.log('Login failed, skipping STT test');
        return;
    }

    group('STT 녹음 플로우', () => {
        let sttId = null;

        // 2. 녹음 시작
        group('녹음 시작', () => {
            const startRes = http.post(
                `${BASE_URL}/stt/recording/start`,
                JSON.stringify({ meetingId: parseInt(MEETING_ID) }),
                headers(token)
            );

            sttStartDuration.add(startRes.timings.duration);

            const startOk = check(startRes, {
                '녹음 시작 200': (r) => r.status === 200,
                '녹음 시작 응답에 id 포함': (r) => {
                    try {
                        const body = JSON.parse(r.body);
                        return body.id !== undefined;
                    } catch {
                        return false;
                    }
                },
                '상태가 RECORDING': (r) => {
                    try {
                        const body = JSON.parse(r.body);
                        return body.status === 'RECORDING';
                    } catch {
                        return false;
                    }
                },
            });

            if (startOk) {
                const body = JSON.parse(startRes.body);
                sttId = body.id;
            } else {
                errorRate.add(1);
            }
        });

        if (!sttId) {
            console.log('STT 시작 실패, 청크 업로드 스킵');
            return;
        }

        // 3. 청크 업로드 (5회)
        group('청크 업로드', () => {
            const chunkCount = 5;

            for (let i = 0; i < chunkCount; i++) {
                const isLastChunk = i === chunkCount - 1;
                const audioChunk = generateAudioChunk(8192); // 8KB 청크

                const fd = new FormData();
                fd.append('file', http.file(audioChunk, `chunk-${i}.wav`, 'audio/wav'));

                if (isLastChunk) {
                    fd.append('finish', 'true');
                }

                const chunkRes = http.post(
                    `${BASE_URL}/stt/${sttId}/chunk`,
                    fd.body(),
                    {
                        headers: {
                            Authorization: token,
                            'Content-Type': `multipart/form-data; boundary=${fd.boundary}`,
                        },
                    }
                );

                sttChunkDuration.add(chunkRes.timings.duration);

                const chunkOk = check(chunkRes, {
                    [`청크 ${i + 1} 업로드 200`]: (r) => r.status === 200,
                });

                if (!chunkOk) {
                    errorRate.add(1);
                }

                // 청크 간 딜레이 (실제 녹음 시뮬레이션)
                sleep(0.3);
            }
        });

        // 4. 상태 폴링 (최대 10회)
        group('상태 폴링', () => {
            const maxPolls = 10;
            let completed = false;

            for (let i = 0; i < maxPolls && !completed; i++) {
                const statusRes = http.get(
                    `${BASE_URL}/stt/status/${sttId}`,
                    headers(token)
                );

                sttStatusDuration.add(statusRes.timings.duration);

                const statusOk = check(statusRes, {
                    '상태 조회 200': (r) => r.status === 200,
                });

                if (statusOk) {
                    try {
                        const body = JSON.parse(statusRes.body);
                        const status = body.status;

                        // ENCODING, ENCODED 상태면 계속 폴링
                        // (실제 STT 변환까지는 외부 API 의존이므로 여기서는 ENCODED까지만 확인)
                        if (status === 'ENCODED' || status === 'PROCESSING' ||
                            status === 'SUMMARIZING' || status === 'COMPLETED') {
                            completed = true;
                        }
                    } catch (e) {
                        console.log('상태 파싱 실패:', e);
                    }
                } else {
                    errorRate.add(1);
                }

                if (!completed) {
                    sleep(1); // 1초 대기 후 재폴링
                }
            }

            if (completed) {
                successfulSessions.add(1);
            }
        });
    });

    const sessionEnd = Date.now();
    sttTotalDuration.add(sessionEnd - sessionStart);

    // 세션 간 딜레이
    sleep(1);
}

// ─── 라이프사이클 훅 ───
export function setup() {
    console.log('STT 부하 테스트 시작');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`Meeting ID: ${MEETING_ID}`);

    // 서버 헬스체크
    const healthRes = http.get(`${BASE_URL}/actuator/health`, { timeout: '5s' });
    if (healthRes.status !== 200) {
        console.warn('서버 헬스체크 실패, 테스트 계속 진행');
    }

    return { startTime: Date.now() };
}

export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`STT 부하 테스트 완료 - 총 소요 시간: ${duration.toFixed(2)}초`);
}
