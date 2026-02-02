import { check, sleep, group } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomBytes } from 'k6/crypto';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

// ─── 커스텀 메트릭 ───
const wsConnectDuration = new Trend('ws_connect_duration', true);
const wsMessageLatency = new Trend('ws_message_latency', true);
const wsMessages = new Counter('ws_messages_received');
const wsErrors = new Rate('ws_errors');
const sttUpdatesReceived = new Counter('stt_updates_received');

// ─── 설정 ───
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';
const LOGIN_ID = __ENV.LOGIN_ID || 'testuser';
const PASSWORD = __ENV.PASSWORD || 'password1234';
const MEETING_ID = __ENV.MEETING_ID || '1';

// ─── 부하 시나리오 ───
export const options = {
    scenarios: {
        // 1) WebSocket 연결 테스트
        ws_connections: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 5 },    // 5 동시 연결
                { duration: '1m', target: 5 },     // 유지
                { duration: '30s', target: 10 },   // 10 동시 연결
                { duration: '1m', target: 10 },    // 유지
                { duration: '30s', target: 0 },    // 종료
            ],
            tags: { test_type: 'websocket' },
        },
    },
    thresholds: {
        ws_connect_duration: ['p(95)<3000'],      // WebSocket 연결: p95 < 3초
        ws_message_latency: ['p(95)<500'],        // 메시지 레이턴시: p95 < 500ms
        ws_errors: ['rate<0.1'],                  // WebSocket 에러율 < 10%
    },
};

// ─── 헬퍼 함수 ───
function login(loginId, password) {
    const res = http.post(
        `${BASE_URL}/login`,
        JSON.stringify({ loginId, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status === 200) {
        const body = JSON.parse(res.body);
        return body.token;
    }
    return null;
}

function headers(token) {
    return {
        headers: {
            Authorization: token,
            'Content-Type': 'application/json',
        },
    };
}

// STOMP 프레임 생성
function createStompFrame(command, headers = {}, body = '') {
    let frame = command + '\n';
    for (const [key, value] of Object.entries(headers)) {
        frame += `${key}:${value}\n`;
    }
    frame += '\n' + body + '\0';
    return frame;
}

// STOMP CONNECT 프레임
function stompConnect(token) {
    return createStompFrame('CONNECT', {
        'accept-version': '1.2',
        'host': 'localhost',
        'Authorization': token,
    });
}

// STOMP SUBSCRIBE 프레임
function stompSubscribe(id, destination) {
    return createStompFrame('SUBSCRIBE', {
        'id': id,
        'destination': destination,
    });
}

// STOMP DISCONNECT 프레임
function stompDisconnect() {
    return createStompFrame('DISCONNECT', {
        'receipt': 'disconnect-receipt',
    });
}

// 테스트용 오디오 청크 생성
function generateAudioChunk(sizeBytes = 4096) {
    const header = new Uint8Array([
        0x52, 0x49, 0x46, 0x46,
        0x00, 0x00, 0x00, 0x00,
        0x57, 0x41, 0x56, 0x45,
        0x66, 0x6D, 0x74, 0x20,
        0x10, 0x00, 0x00, 0x00,
        0x01, 0x00,
        0x01, 0x00,
        0x80, 0x3E, 0x00, 0x00,
        0x00, 0x7D, 0x00, 0x00,
        0x02, 0x00,
        0x10, 0x00,
        0x64, 0x61, 0x74, 0x61,
        0x00, 0x00, 0x00, 0x00,
    ]);

    const audioData = randomBytes(sizeBytes - 44);
    const chunk = new Uint8Array(sizeBytes);
    chunk.set(header, 0);
    chunk.set(new Uint8Array(audioData), 44);

    return chunk;
}

// ─── 메인 시나리오 ───
export default function () {
    // 1. 로그인
    const token = login(LOGIN_ID, PASSWORD);
    if (!token) {
        wsErrors.add(1);
        console.log('Login failed');
        return;
    }

    // 2. STT 녹음 시작
    const startRes = http.post(
        `${BASE_URL}/stt/recording/start`,
        JSON.stringify({ meetingId: parseInt(MEETING_ID) }),
        headers(token)
    );

    if (startRes.status !== 200) {
        wsErrors.add(1);
        console.log('Failed to start recording');
        return;
    }

    const sttData = JSON.parse(startRes.body);
    const sttId = sttData.id;
    const meetingId = sttData.meetingId || MEETING_ID;

    console.log(`STT started: ${sttId}, Meeting: ${meetingId}`);

    // 3. WebSocket 연결 및 STOMP 구독
    group('WebSocket 연결 및 구독', () => {
        const connectStart = Date.now();

        const wsResponse = ws.connect(WS_URL, { headers: { Authorization: token } }, function (socket) {
            const connectDuration = Date.now() - connectStart;
            wsConnectDuration.add(connectDuration);

            let connected = false;
            let messageCount = 0;

            socket.on('open', () => {
                console.log('WebSocket connected');

                // STOMP CONNECT
                socket.send(stompConnect(token));
            });

            socket.on('message', (message) => {
                const receiveTime = Date.now();

                wsMessages.add(1);
                messageCount++;

                // STOMP 프레임 파싱
                if (message.startsWith('CONNECTED')) {
                    connected = true;
                    console.log('STOMP connected');

                    // STT 업데이트 구독
                    const subscribeFrame = stompSubscribe(
                        'sub-stt-' + sttId,
                        `/topic/stt/updates/${meetingId}`
                    );
                    socket.send(subscribeFrame);

                    console.log(`Subscribed to /topic/stt/updates/${meetingId}`);

                    // 청크 업로드 시작 (비동기)
                    uploadChunks(sttId, token);
                }

                if (message.startsWith('MESSAGE')) {
                    // STT 업데이트 메시지 수신
                    sttUpdatesReceived.add(1);

                    // 메시지 레이턴시 측정 (대략적)
                    const latency = Date.now() - receiveTime;
                    wsMessageLatency.add(latency);

                    // 메시지 내용 파싱
                    try {
                        const bodyStart = message.indexOf('\n\n') + 2;
                        const body = message.substring(bodyStart, message.length - 1);
                        const data = JSON.parse(body);

                        console.log(`STT Update - Status: ${data.status}, Progress: ${data.progress || 'N/A'}`);

                        // COMPLETED 상태면 테스트 종료
                        if (data.status === 'COMPLETED' || data.status === 'ENCODED') {
                            console.log('STT processing completed');
                        }
                    } catch (e) {
                        // JSON 파싱 실패 무시
                    }
                }

                if (message.startsWith('ERROR')) {
                    wsErrors.add(1);
                    console.log('STOMP ERROR:', message);
                }
            });

            socket.on('error', (e) => {
                wsErrors.add(1);
                console.log('WebSocket error:', e);
            });

            socket.on('close', () => {
                console.log(`WebSocket closed. Messages received: ${messageCount}`);
            });

            // 60초간 연결 유지
            socket.setTimeout(() => {
                if (connected) {
                    socket.send(stompDisconnect());
                }
                socket.close();
            }, 60000);
        });

        check(wsResponse, {
            'WebSocket 연결 성공': (r) => r && r.status === 101,
        });
    });

    sleep(5);
}

// 청크 업로드 함수 (별도 HTTP 요청)
function uploadChunks(sttId, token) {
    const chunkCount = 5;

    for (let i = 0; i < chunkCount; i++) {
        const isLastChunk = i === chunkCount - 1;
        const audioChunk = generateAudioChunk(4096);

        const fd = new FormData();
        fd.append('file', http.file(audioChunk, `chunk-${i}.wav`, 'audio/wav'));

        if (isLastChunk) {
            fd.append('finish', 'true');
        }

        http.post(
            `${BASE_URL}/stt/${sttId}/chunk`,
            fd.body(),
            {
                headers: {
                    Authorization: token,
                    'Content-Type': `multipart/form-data; boundary=${fd.boundary}`,
                },
            }
        );

        sleep(0.5);
    }
}

// ─── 라이프사이클 훅 ───
export function setup() {
    console.log('STT WebSocket 테스트 시작');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`WebSocket URL: ${WS_URL}`);
    console.log(`Meeting ID: ${MEETING_ID}`);

    return { startTime: Date.now() };
}

export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`STT WebSocket 테스트 완료 - 총 소요 시간: ${duration.toFixed(2)}초`);
}
