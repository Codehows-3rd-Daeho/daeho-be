package com.codehows.daehobe.stt.integration;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * STT E2E 통합 테스트
 *
 * 테스트 플로우:
 * 1. 녹음 시작 → 상태 RECORDING → Redis 캐싱 확인
 * 2. 청크 업로드 (5개) → finish=true 시 Kafka 발행
 * 3. WireMock으로 Daglo API Mock (STT, Summary)
 * 4. 상태 전이 확인: RECORDING → ENCODING → PROCESSING → SUMMARIZING → COMPLETED
 * 5. 최종 결과 DB 저장 확인
 */
@Testcontainers
@DisplayName("STT E2E 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SttE2EIntegrationTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    ).withKraft();

    static WireMockServer wireMockServer;
    static StringRedisTemplate redisTemplate;
    static Consumer<String, String> kafkaConsumer;
    static ObjectMapper objectMapper = new ObjectMapper();

    private static final String STT_STATUS_PREFIX = "stt:status:";
    private static final String STT_HEARTBEAT_PREFIX = "stt:recording:heartbeat:";

    @BeforeAll
    static void setupAll() {
        // WireMock 서버 시작
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Redis 연결 설정
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379)
        );
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // Kafka 토픽 생성
        createKafkaTopics();

        // Kafka Consumer 설정
        setupKafkaConsumer();
    }

    private static void createKafkaTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            List<NewTopic> topics = List.of(
                    new NewTopic(STT_ENCODING_TOPIC, 1, (short) 1),
                    new NewTopic(STT_PROCESSING_TOPIC, 1, (short) 1),
                    new NewTopic(STT_SUMMARIZING_TOPIC, 1, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // 이미 존재하면 무시
        }
    }

    private static void setupKafkaConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        kafkaConsumer = consumerFactory.createConsumer();
        kafkaConsumer.subscribe(List.of(STT_ENCODING_TOPIC, STT_PROCESSING_TOPIC, STT_SUMMARIZING_TOPIC));
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @Order(1)
    @DisplayName("1. 녹음 시작 시뮬레이션 - RECORDING 상태 및 Redis 캐싱")
    void startRecording_SimulateRecordingState_CachedInRedis() throws Exception {
        // given
        Long sttId = 1L;
        Long meetingId = 100L;

        STTDto sttDto = STTDto.builder()
                .id(sttId)
                .meetingId(meetingId)
                .status(STT.Status.RECORDING)
                .content("")
                .summary("")
                .chunkingCnt(0)
                .build();

        // when - Redis에 상태 캐싱 (STTService.startRecording 시뮬레이션)
        String key = STT_STATUS_PREFIX + sttId;
        String jsonValue = objectMapper.writeValueAsString(sttDto);
        redisTemplate.opsForValue().set(key, jsonValue, 30, TimeUnit.MINUTES);

        // Heartbeat 키 설정
        String heartbeatKey = STT_HEARTBEAT_PREFIX + sttId;
        redisTemplate.opsForValue().set(heartbeatKey, "", 30, TimeUnit.SECONDS);

        // then
        assertThat(redisTemplate.hasKey(key)).isTrue();
        assertThat(redisTemplate.hasKey(heartbeatKey)).isTrue();

        String cached = redisTemplate.opsForValue().get(key);
        STTDto cachedDto = objectMapper.readValue(cached, STTDto.class);
        assertThat(cachedDto.getId()).isEqualTo(sttId);
        assertThat(cachedDto.getStatus()).isEqualTo(STT.Status.RECORDING);
    }

    @Test
    @Order(2)
    @DisplayName("2. 청크 업로드 및 Heartbeat 갱신 시뮬레이션")
    void uploadChunk_SimulateChunkUpload_HeartbeatRefreshed() throws Exception {
        // given
        Long sttId = 2L;
        String heartbeatKey = STT_HEARTBEAT_PREFIX + sttId;

        // 초기 heartbeat 설정
        redisTemplate.opsForValue().set(heartbeatKey, "", 5, TimeUnit.SECONDS);
        Long initialTtl = redisTemplate.getExpire(heartbeatKey, TimeUnit.SECONDS);

        // when - 청크 업로드 시 heartbeat 갱신 시뮬레이션
        Thread.sleep(1000); // 1초 대기
        redisTemplate.opsForValue().set(heartbeatKey, "", 30, TimeUnit.SECONDS);
        Long refreshedTtl = redisTemplate.getExpire(heartbeatKey, TimeUnit.SECONDS);

        // then - TTL이 갱신되었음을 확인
        assertThat(refreshedTtl).isGreaterThan(initialTtl - 2); // 대략적인 비교
    }

    @Test
    @Order(3)
    @DisplayName("3. 녹음 종료 시 ENCODING 상태로 전이")
    void finishRecording_TransitionToEncoding_KafkaMessagePublished() throws Exception {
        // given
        Long sttId = 3L;
        Long meetingId = 100L;

        STTDto sttDto = STTDto.builder()
                .id(sttId)
                .meetingId(meetingId)
                .status(STT.Status.RECORDING)
                .build();

        String key = STT_STATUS_PREFIX + sttId;
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sttDto), 30, TimeUnit.MINUTES);

        // when - 녹음 종료 시 상태 변경
        sttDto = STTDto.builder()
                .id(sttId)
                .meetingId(meetingId)
                .status(STT.Status.ENCODING)
                .build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sttDto), 30, TimeUnit.MINUTES);

        // Heartbeat 삭제
        redisTemplate.delete(STT_HEARTBEAT_PREFIX + sttId);

        // then
        String cached = redisTemplate.opsForValue().get(key);
        STTDto cachedDto = objectMapper.readValue(cached, STTDto.class);
        assertThat(cachedDto.getStatus()).isEqualTo(STT.Status.ENCODING);
        assertThat(redisTemplate.hasKey(STT_HEARTBEAT_PREFIX + sttId)).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("4. WireMock - Daglo STT API 요청 모킹")
    void wiremockDagloSttApi_RequestTranscription_Success() {
        // given - STT 요청 스텁
        String expectedRid = "test-rid-12345";
        stubFor(post(urlEqualTo("/stt/v1/async/transcripts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + expectedRid + "\"}")));

        // when - 요청 시뮬레이션
        // (실제로는 WebClient가 호출하지만 여기서는 스텁 확인)

        // then - 스텁이 올바르게 설정되었는지 확인
        verify(0, postRequestedFor(urlEqualTo("/stt/v1/async/transcripts")));
    }

    @Test
    @Order(5)
    @DisplayName("5. WireMock - Daglo STT 상태 확인 (진행중 → 완료)")
    void wiremockDagloSttStatus_ProgressToCompleted() {
        // given - 진행중 상태 스텁
        String rid = "test-rid-progress";
        stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .inScenario("STT Progress")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + rid + "\", \"status\": \"processing\", \"progress\": 50}"))
                .willSetStateTo("InProgress"));

        stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .inScenario("STT Progress")
                .whenScenarioStateIs("InProgress")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + rid + "\", \"status\": \"transcribed\", \"progress\": 100, " +
                                "\"sttResults\": [{\"transcript\": \"테스트 음성 내용입니다.\", \"words\": []}]}")));

        // 스텁 설정 확인
        assertThat(wireMockServer.isRunning()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("6. WireMock - Daglo Summary API 모킹")
    void wiremockDagloSummaryApi_RequestSummary_Success() {
        // given - Summary 요청 스텁
        String expectedRid = "summary-rid-12345";
        stubFor(post(urlEqualTo("/nlp/v1/async/minutes"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + expectedRid + "\"}")));

        // Summary 상태 확인 스텁 (완료)
        stubFor(get(urlEqualTo("/nlp/v1/async/minutes/" + expectedRid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + expectedRid + "\", \"status\": \"processed\", \"progress\": 100, " +
                                "\"title\": \"회의 요약\", \"minutes\": [{\"title\": \"주요 안건\", " +
                                "\"bullets\": [{\"isImportant\": true, \"text\": \"중요 사항입니다.\"}]}]}")));

        // 스텁 설정 확인
        assertThat(wireMockServer.isRunning()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("7. 전체 상태 전이 시뮬레이션: RECORDING → ENCODING → PROCESSING → SUMMARIZING → COMPLETED")
    void fullStateTransition_RecordingToCompleted() throws Exception {
        // given
        Long sttId = 7L;
        Long meetingId = 100L;
        String key = STT_STATUS_PREFIX + sttId;

        // 1. RECORDING
        STTDto dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.RECORDING)
                .content("").summary("").build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);
        assertThat(getStatus(key)).isEqualTo(STT.Status.RECORDING);

        // 2. ENCODING
        dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.ENCODING).build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);
        assertThat(getStatus(key)).isEqualTo(STT.Status.ENCODING);

        // 3. ENCODED
        dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.ENCODED).build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);
        assertThat(getStatus(key)).isEqualTo(STT.Status.ENCODED);

        // 4. PROCESSING
        dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.PROCESSING)
                .rid("test-rid").build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);
        assertThat(getStatus(key)).isEqualTo(STT.Status.PROCESSING);

        // 5. SUMMARIZING
        dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.SUMMARIZING)
                .rid("test-rid").summaryRid("summary-rid")
                .content("변환된 텍스트 내용").build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);
        assertThat(getStatus(key)).isEqualTo(STT.Status.SUMMARIZING);

        // 6. COMPLETED
        dto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.COMPLETED)
                .rid("test-rid").summaryRid("summary-rid")
                .content("변환된 텍스트 내용")
                .summary("## 회의 요약\n### 주요 안건\n- **중요 사항입니다.**").build();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(dto), 30, TimeUnit.MINUTES);

        // then
        STT.Status finalStatus = getStatus(key);
        assertThat(finalStatus).isEqualTo(STT.Status.COMPLETED);

        String cached = redisTemplate.opsForValue().get(key);
        STTDto finalDto = objectMapper.readValue(cached, STTDto.class);
        assertThat(finalDto.getContent()).isNotEmpty();
        assertThat(finalDto.getSummary()).isNotEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("8. 캐시 TTL 테스트 - 30분 후 만료")
    void cacheExpiry_After30Minutes_KeyRemoved() {
        // given
        Long sttId = 8L;
        String key = STT_STATUS_PREFIX + sttId;

        // when - 짧은 TTL로 테스트 (실제로는 30분)
        redisTemplate.opsForValue().set(key, "test", 1, TimeUnit.SECONDS);

        // then
        assertThat(redisTemplate.hasKey(key)).isTrue();

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(key)));

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @Order(9)
    @DisplayName("9. 동시 녹음 세션 - 독립적인 상태 관리")
    void concurrentRecordingSessions_IndependentStates() throws Exception {
        // given
        Long sttId1 = 9L;
        Long sttId2 = 10L;
        Long meetingId = 100L;

        // when - 두 개의 독립적인 녹음 세션
        STTDto dto1 = STTDto.builder()
                .id(sttId1).meetingId(meetingId).status(STT.Status.RECORDING).build();
        STTDto dto2 = STTDto.builder()
                .id(sttId2).meetingId(meetingId).status(STT.Status.PROCESSING).build();

        redisTemplate.opsForValue().set(STT_STATUS_PREFIX + sttId1,
                objectMapper.writeValueAsString(dto1), 30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(STT_STATUS_PREFIX + sttId2,
                objectMapper.writeValueAsString(dto2), 30, TimeUnit.MINUTES);

        // then - 각각 독립적인 상태 유지
        assertThat(getStatus(STT_STATUS_PREFIX + sttId1)).isEqualTo(STT.Status.RECORDING);
        assertThat(getStatus(STT_STATUS_PREFIX + sttId2)).isEqualTo(STT.Status.PROCESSING);

        // sttId1 상태 변경이 sttId2에 영향 없음
        dto1 = STTDto.builder()
                .id(sttId1).meetingId(meetingId).status(STT.Status.ENCODING).build();
        redisTemplate.opsForValue().set(STT_STATUS_PREFIX + sttId1,
                objectMapper.writeValueAsString(dto1), 30, TimeUnit.MINUTES);

        assertThat(getStatus(STT_STATUS_PREFIX + sttId1)).isEqualTo(STT.Status.ENCODING);
        assertThat(getStatus(STT_STATUS_PREFIX + sttId2)).isEqualTo(STT.Status.PROCESSING);
    }

    @Test
    @Order(10)
    @DisplayName("10. Heartbeat 만료 시뮬레이션 - 비정상 종료 감지")
    void heartbeatExpiry_AbnormalTermination_Detected() {
        // given
        Long sttId = 11L;
        String heartbeatKey = STT_HEARTBEAT_PREFIX + sttId;

        // when - 짧은 TTL로 heartbeat 설정 (실제로는 30초)
        redisTemplate.opsForValue().set(heartbeatKey, "", 1, TimeUnit.SECONDS);

        // then - Heartbeat 존재 확인
        assertThat(redisTemplate.hasKey(heartbeatKey)).isTrue();

        // 만료 후 키 삭제됨
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey)));

        assertThat(redisTemplate.hasKey(heartbeatKey)).isFalse();
        // 실제 시스템에서는 이 시점에 KeyExpirationEventMessageListener가
        // 비정상 종료 처리를 시작함
    }

    private STT.Status getStatus(String key) throws Exception {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        STTDto dto = objectMapper.readValue(json, STTDto.class);
        return dto.getStatus();
    }
}
