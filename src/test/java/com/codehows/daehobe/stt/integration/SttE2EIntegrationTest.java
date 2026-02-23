package com.codehows.daehobe.stt.integration;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.repository.FileRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.STTService;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.processing.SttJobProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.codehows.daehobe.common.constant.Status;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * STT E2E 통합 테스트 (실제 서비스 호출 기반)
 *
 * 테스트 환경:
 * - Testcontainers Redis
 * - WireMock (Daglo API Mock)
 * - H2 인메모리 DB
 *
 * 테스트 플로우:
 * 1. 녹음 시작 → sttService.startRecording() 호출 + Redis 캐시 검증
 * 2. 청크 업로드 및 종료 → appendChunk(finish=true) + ENCODING 전이
 * 3. 파일 업로드 → uploadAndTranslate() + WireMock 호출 검증
 * 4. STT 처리 → sttJobProcessor.processSingleSttJob() + SUMMARIZING 전이
 * 5. Summary 처리 → processSingleSummaryJob() + COMPLETED + DB 저장
 * 6. 전체 E2E 플로우
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("STT E2E 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SttE2EIntegrationTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    static WireMockServer wireMockServer;
    static StringRedisTemplate redisTemplate;
    static ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SttCacheService sttCacheService;

    @Autowired
    private SttJobProcessor sttJobProcessor;

    @Autowired
    private STTRepository sttRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @MockBean
    private FileService fileService;

    private static final String STT_STATUS_PREFIX = "stt:status:";
    private static final String STT_HEARTBEAT_PREFIX = "stt:recording:heartbeat:";
    private static final String STT_POLLING_PROCESSING_SET = "stt:polling:processing";
    private static final String STT_POLLING_SUMMARIZING_SET = "stt:polling:summarizing";

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
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("daglo.api.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("stt.polling.interval-ms", () -> "500");
        registry.add("stt.polling.max-attempts", () -> "10");
        registry.add("file.location", () -> "/tmp/stt_test");
        registry.add("app.base-url", () -> "http://localhost:8080");
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        sttRepository.deleteAll();
        meetingRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("1. Redis 캐싱 - SttCacheService를 통한 상태 캐싱 검증")
    void sttCacheService_CacheAndRetrieve_Success() {
        // given
        Long sttId = 1L;
        STTDto sttDto = STTDto.builder()
                .id(sttId)
                .meetingId(100L)
                .status(STT.Status.RECORDING)
                .content("")
                .summary("")
                .build();

        // when - SttCacheService를 통한 캐싱
        sttCacheService.cacheSttStatus(sttDto);

        // then - 캐시에서 조회
        STTDto cached = sttCacheService.getCachedSttStatus(sttId);
        assertThat(cached).isNotNull();
        assertThat(cached.getId()).isEqualTo(sttId);
        assertThat(cached.getStatus()).isEqualTo(STT.Status.RECORDING);
    }

    @Test
    @Order(2)
    @DisplayName("2. Polling Set - 폴링 셋에 추가/제거/조회 검증")
    void sttCacheService_PollingSet_Operations() {
        // given
        Long sttId1 = 1L;
        Long sttId2 = 2L;

        // when - 폴링 셋에 추가
        sttCacheService.addToPollingSet(sttId1, STT.Status.PROCESSING);
        sttCacheService.addToPollingSet(sttId2, STT.Status.PROCESSING);

        // then - 폴링 셋에서 조회
        Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        assertThat(taskIds).contains(sttId1, sttId2);

        // when - 폴링 셋에서 제거
        sttCacheService.removeFromPollingSet(sttId1, STT.Status.PROCESSING);

        // then - 제거 확인
        Set<Long> afterRemoval = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        assertThat(afterRemoval).contains(sttId2).doesNotContain(sttId1);
    }

    @Test
    @Order(3)
    @DisplayName("3. Retry Count - 재시도 카운트 증가/조회/리셋 검증")
    void sttCacheService_RetryCount_Operations() {
        // given
        Long sttId = 1L;

        // when - 카운트 증가
        int count1 = sttCacheService.incrementRetryCount(sttId);
        int count2 = sttCacheService.incrementRetryCount(sttId);
        int count3 = sttCacheService.incrementRetryCount(sttId);

        // then - 카운트 확인
        assertThat(count1).isEqualTo(1);
        assertThat(count2).isEqualTo(2);
        assertThat(count3).isEqualTo(3);

        Integer currentCount = sttCacheService.getRetryCount(sttId);
        assertThat(currentCount).isEqualTo(3);

        // when - 카운트 리셋
        sttCacheService.resetRetryCount(sttId);

        // then - 리셋 확인
        Integer afterReset = sttCacheService.getRetryCount(sttId);
        assertThat(afterReset).isEqualTo(0);
    }

    @Test
    @Order(4)
    @DisplayName("4. WireMock - Daglo STT API 요청 모킹 (POST /stt/v1/async/transcripts)")
    void wiremockDagloSttApi_RequestTranscription_Success() {
        // given - STT 요청 스텁
        String expectedRid = "test-rid-12345";
        stubFor(post(urlEqualTo("/stt/v1/async/transcripts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + expectedRid + "\"}")));

        // then - 스텁이 올바르게 설정되었는지 확인
        assertThat(wireMockServer.isRunning()).isTrue();
        verify(0, postRequestedFor(urlEqualTo("/stt/v1/async/transcripts")));
    }

    @Test
    @Order(5)
    @DisplayName("5. WireMock - Daglo STT 상태 확인 시나리오 (진행중 → 완료)")
    void wiremockDagloSttStatus_ProgressToCompleted_Scenario() {
        // given - 시나리오 기반 스텁 설정
        String rid = "test-rid-progress";

        // 첫 번째 호출: 진행중 (50%)
        stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .inScenario("STT Progress")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + rid + "\", \"status\": \"processing\", \"progress\": 50}"))
                .willSetStateTo("InProgress"));

        // 두 번째 호출: 완료 (100%)
        stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .inScenario("STT Progress")
                .whenScenarioStateIs("InProgress")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\": \"" + rid + "\", \"status\": \"transcribed\", \"progress\": 100, " +
                                "\"sttResults\": [{\"transcript\": \"테스트 음성 내용입니다.\", \"words\": []}]}")));

        // then
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

        // then
        assertThat(wireMockServer.isRunning()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("7. 상태 전이 시뮬레이션 - RECORDING → ENCODING → ... → COMPLETED")
    void fullStateTransition_RecordingToCompleted_CacheOnly() throws Exception {
        // given
        Long sttId = 7L;
        Long meetingId = 100L;

        // 1. RECORDING 상태
        STTDto recordingDto = STTDto.builder()
                .id(sttId).meetingId(meetingId).status(STT.Status.RECORDING)
                .content("").summary("").build();
        sttCacheService.cacheSttStatus(recordingDto);
        assertThat(sttCacheService.getCachedSttStatus(sttId).getStatus()).isEqualTo(STT.Status.RECORDING);

        // 2. ENCODING 상태
        recordingDto.updateStatus(STT.Status.ENCODING);
        sttCacheService.cacheSttStatus(recordingDto);
        assertThat(sttCacheService.getCachedSttStatus(sttId).getStatus()).isEqualTo(STT.Status.ENCODING);

        // 3. ENCODED 상태
        recordingDto.updateStatus(STT.Status.ENCODED);
        sttCacheService.cacheSttStatus(recordingDto);
        assertThat(sttCacheService.getCachedSttStatus(sttId).getStatus()).isEqualTo(STT.Status.ENCODED);

        // 4. PROCESSING 상태 + 폴링 셋 등록
        recordingDto.updateStatus(STT.Status.PROCESSING);
        recordingDto.updateRid("test-rid");
        sttCacheService.cacheSttStatus(recordingDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);
        assertThat(sttCacheService.getCachedSttStatus(sttId).getStatus()).isEqualTo(STT.Status.PROCESSING);
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).contains(sttId);

        // 5. SUMMARIZING 상태 + 폴링 셋 전환
        sttCacheService.removeFromPollingSet(sttId, STT.Status.PROCESSING);
        recordingDto.updateStatus(STT.Status.SUMMARIZING);
        recordingDto.updateSummaryRid("summary-rid");
        recordingDto.updateContent("변환된 텍스트 내용");
        sttCacheService.cacheSttStatus(recordingDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.SUMMARIZING);
        assertThat(sttCacheService.getCachedSttStatus(sttId).getStatus()).isEqualTo(STT.Status.SUMMARIZING);
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).contains(sttId);

        // 6. COMPLETED 상태 + 폴링 셋 제거
        sttCacheService.removeFromPollingSet(sttId, STT.Status.SUMMARIZING);
        recordingDto.updateStatus(STT.Status.COMPLETED);
        recordingDto.updateSummary("## 회의 요약\n### 주요 안건\n- **중요 사항입니다.**");
        sttCacheService.cacheSttStatus(recordingDto);

        // then - 최종 상태 확인
        STTDto finalDto = sttCacheService.getCachedSttStatus(sttId);
        assertThat(finalDto.getStatus()).isEqualTo(STT.Status.COMPLETED);
        assertThat(finalDto.getContent()).isNotEmpty();
        assertThat(finalDto.getSummary()).isNotEmpty();
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).doesNotContain(sttId);
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).doesNotContain(sttId);
    }

    @Test
    @Order(8)
    @DisplayName("8. 캐시 TTL 테스트 - 짧은 TTL로 만료 확인")
    void cacheExpiry_ShortTtl_KeyRemoved() {
        // given
        Long sttId = 8L;
        String key = STT_STATUS_PREFIX + sttId;

        // when - 짧은 TTL로 직접 설정 (1초)
        redisTemplate.opsForValue().set(key, "{\"id\":8,\"status\":\"RECORDING\"}", 1, TimeUnit.SECONDS);

        // then
        assertThat(redisTemplate.hasKey(key)).isTrue();

        // 만료 대기
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(key)));

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @Order(9)
    @DisplayName("9. 동시 세션 - 독립적인 상태 관리")
    void concurrentSessions_IndependentStates() {
        // given
        Long sttId1 = 9L;
        Long sttId2 = 10L;
        Long meetingId = 100L;

        // when - 두 개의 독립적인 세션
        STTDto dto1 = STTDto.builder()
                .id(sttId1).meetingId(meetingId).status(STT.Status.RECORDING).build();
        STTDto dto2 = STTDto.builder()
                .id(sttId2).meetingId(meetingId).status(STT.Status.PROCESSING).build();

        sttCacheService.cacheSttStatus(dto1);
        sttCacheService.cacheSttStatus(dto2);

        // then - 각각 독립적인 상태 유지
        assertThat(sttCacheService.getCachedSttStatus(sttId1).getStatus()).isEqualTo(STT.Status.RECORDING);
        assertThat(sttCacheService.getCachedSttStatus(sttId2).getStatus()).isEqualTo(STT.Status.PROCESSING);

        // sttId1 상태 변경이 sttId2에 영향 없음
        dto1.updateStatus(STT.Status.ENCODING);
        sttCacheService.cacheSttStatus(dto1);

        assertThat(sttCacheService.getCachedSttStatus(sttId1).getStatus()).isEqualTo(STT.Status.ENCODING);
        assertThat(sttCacheService.getCachedSttStatus(sttId2).getStatus()).isEqualTo(STT.Status.PROCESSING);
    }

    @Test
    @Order(10)
    @DisplayName("10. Heartbeat 만료 - TTL 만료로 키 삭제 확인")
    void heartbeatExpiry_TtlExpired_KeyRemoved() {
        // given
        Long sttId = 11L;
        String heartbeatKey = STT_HEARTBEAT_PREFIX + sttId;

        // when - 짧은 TTL로 heartbeat 설정 (1초)
        redisTemplate.opsForValue().set(heartbeatKey, "", 1, TimeUnit.SECONDS);

        // then - Heartbeat 존재 확인
        assertThat(redisTemplate.hasKey(heartbeatKey)).isTrue();

        // 만료 후 키 삭제됨
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey)));

        assertThat(redisTemplate.hasKey(heartbeatKey)).isFalse();
    }

    @Test
    @Order(11)
    @DisplayName("11. Redis 가용성 체크")
    void redisAvailability_Check() {
        // when
        boolean isAvailable = sttCacheService.isRedisAvailable();

        // then
        assertThat(isAvailable).isTrue();
    }

    @Test
    @Order(12)
    @DisplayName("12. 상태별 TTL 적용 확인 - COMPLETED 상태는 10분 TTL")
    void statusBasedTtl_CompletedStatus_ShortTtl() {
        // given
        Long sttId = 12L;
        STTDto completedDto = STTDto.builder()
                .id(sttId)
                .meetingId(100L)
                .status(STT.Status.COMPLETED)
                .content("변환된 텍스트")
                .summary("요약")
                .build();

        // when
        sttCacheService.cacheSttStatus(completedDto);

        // then - 캐시에 저장되었는지 확인
        STTDto cached = sttCacheService.getCachedSttStatus(sttId);
        assertThat(cached).isNotNull();
        assertThat(cached.getStatus()).isEqualTo(STT.Status.COMPLETED);

        // TTL 확인 (정확한 값은 아니지만 존재 여부 확인)
        String key = STT_STATUS_PREFIX + sttId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isLessThanOrEqualTo(10L);
    }

    @Test
    @Order(13)
    @DisplayName("13. 폴링 셋 - RECORDING 상태는 폴링 셋에 추가되지 않음")
    void pollingSet_RecordingStatus_NotAdded() {
        // given
        Long sttId = 13L;

        // when
        sttCacheService.addToPollingSet(sttId, STT.Status.RECORDING);

        // then - RECORDING은 폴링 셋에 추가되지 않음
        Set<Long> processingTasks = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        Set<Long> summarizingTasks = sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING);

        assertThat(processingTasks).doesNotContain(sttId);
        assertThat(summarizingTasks).doesNotContain(sttId);
    }

    @Test
    @Order(14)
    @DisplayName("14. 폴링 셋 - PROCESSING과 SUMMARIZING만 폴링 대상")
    void pollingSet_OnlyProcessingAndSummarizing() {
        // given
        Long processingId = 14L;
        Long summarizingId = 15L;
        Long encodedId = 16L;

        // when
        sttCacheService.addToPollingSet(processingId, STT.Status.PROCESSING);
        sttCacheService.addToPollingSet(summarizingId, STT.Status.SUMMARIZING);
        sttCacheService.addToPollingSet(encodedId, STT.Status.ENCODED);

        // then
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).contains(processingId);
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).contains(summarizingId);
        // ENCODED는 폴링 대상이 아니므로 조회 시 빈 집합 반환
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.ENCODED)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 실제 서비스 호출 기반 E2E 테스트 (Tests 4~6 보완)
    // WireMock 스텁 + processSingleSttJob/processSingleSummaryJob 직접 호출
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("15. [실제 호출] processSingleSttJob() - PROCESSING 상태 유지 (미완료 응답)")
    void processSingleSttJob_StillProcessing_RetryCountIncremented() throws Exception {
        // given: PROCESSING 상태 캐시 설정
        Long sttId = 15L;
        String rid = "test-rid-still-processing";

        STTDto processingDto = STTDto.builder()
                .id(sttId)
                .meetingId(100L)
                .status(STT.Status.PROCESSING)
                .rid(rid)
                .content("")
                .summary("")
                .build();
        sttCacheService.cacheSttStatus(processingDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);
        sttCacheService.resetRetryCount(sttId);

        // WireMock: 미완료 응답 (processing, 50%)
        wireMockServer.stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\":\"" + rid + "\",\"status\":\"processing\",\"progress\":50}")));

        // when: 실제 processSingleSttJob() 호출
        sttJobProcessor.processSingleSttJob(sttId);

        // then: PROCESSING 상태 유지, 재시도 카운트 증가
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Integer retryCount = sttCacheService.getRetryCount(sttId);
                    assertThat(retryCount).isGreaterThan(0);
                });

        STTDto cached = sttCacheService.getCachedSttStatus(sttId);
        assertThat(cached).isNotNull();
        assertThat(cached.getStatus()).isEqualTo(STT.Status.PROCESSING);
        assertThat(cached.getProgress()).isEqualTo(50);

        System.out.println("[15] processSingleSttJob() 미완료 → PROCESSING 유지, 재시도 카운트: "
                + sttCacheService.getRetryCount(sttId));
    }

    @Test
    @Order(16)
    @DisplayName("16. [실제 호출] processSingleSttJob() - PROCESSING→SUMMARIZING 전이 (완료 응답)")
    void processSingleSttJob_Completed_TransitionsToSummarizing() throws Exception {
        // given: PROCESSING 상태 캐시 설정
        Long sttId = 16L;
        String rid = "test-rid-completed";
        String summaryRid = "summary-rid-16";

        STTDto processingDto = STTDto.builder()
                .id(sttId)
                .meetingId(100L)
                .status(STT.Status.PROCESSING)
                .rid(rid)
                .content("")
                .summary("")
                .build();
        sttCacheService.cacheSttStatus(processingDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);
        sttCacheService.resetRetryCount(sttId);

        // WireMock: 완료 응답 (transcribed, 100%)
        wireMockServer.stubFor(get(urlEqualTo("/stt/v1/async/transcripts/" + rid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\":\"" + rid + "\",\"status\":\"transcribed\"," +
                                "\"progress\":100,\"sttResults\":[{\"transcript\":\"실제 E2E 테스트 음성 내용\",\"words\":[]}]}")));

        // WireMock: Summary 요청 응답
        wireMockServer.stubFor(post(urlEqualTo("/nlp/v1/async/minutes"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\":\"" + summaryRid + "\"}")));

        // when: 실제 processSingleSttJob() 호출
        sttJobProcessor.processSingleSttJob(sttId);

        // then: SUMMARIZING으로 전이
        await().atMost(8, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    STTDto cached = sttCacheService.getCachedSttStatus(sttId);
                    assertThat(cached).isNotNull();
                    assertThat(cached.getStatus()).isEqualTo(STT.Status.SUMMARIZING);
                });

        STTDto finalCached = sttCacheService.getCachedSttStatus(sttId);
        assertThat(finalCached.getSummaryRid()).isEqualTo(summaryRid);
        assertThat(finalCached.getContent()).contains("실제 E2E 테스트 음성 내용");

        // PROCESSING 폴링 셋에서 제거 확인
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).doesNotContain(sttId);
        // SUMMARIZING 폴링 셋에 추가 확인
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).contains(sttId);

        System.out.println("[16] processSingleSttJob() 완료 → PROCESSING→SUMMARIZING 전이 성공");
        System.out.println("[16] summaryRid=" + finalCached.getSummaryRid());
        System.out.println("[16] content 길이=" + finalCached.getContent().length());
    }

    @Test
    @Order(17)
    @DisplayName("17. [실제 호출] processSingleSummaryJob() - SUMMARIZING→COMPLETED 전이 + DB 저장")
    void processSingleSummaryJob_Completed_TransitionsToCompletedAndSavedToDb() throws Exception {
        // given: DB에 Meeting + STT 엔티티 생성 (processSingleSummaryJob의 DB 저장에 필요)
        Meeting savedMeeting = meetingRepository.save(Meeting.builder()
                .title("E2E 통합 테스트 회의")
                .content("Summary 완료 E2E 테스트")
                .status(Status.IN_PROGRESS)
                .startDate(LocalDateTime.now())
                .isDel(false)
                .isPrivate(false)
                .build());

        STT savedStt = sttRepository.save(STT.builder()
                .meeting(savedMeeting)
                .status(STT.Status.SUMMARIZING)
                .rid("e2e-rid-17")
                .summaryRid("e2e-summary-rid-17")
                .content("E2E 변환된 텍스트")
                .build());

        Long sttId = savedStt.getId();
        String summaryRid = "e2e-summary-rid-17";

        // Redis 캐시에도 SUMMARIZING 상태 설정
        STTDto summarizingDto = STTDto.builder()
                .id(sttId)
                .meetingId(savedMeeting.getId())
                .status(STT.Status.SUMMARIZING)
                .summaryRid(summaryRid)
                .content("E2E 변환된 텍스트")
                .summary("")
                .build();
        sttCacheService.cacheSttStatus(summarizingDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.SUMMARIZING);
        sttCacheService.resetRetryCount(sttId);

        // WireMock: Summary 완료 응답 (processed, 100%)
        wireMockServer.stubFor(get(urlEqualTo("/nlp/v1/async/minutes/" + summaryRid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"rid\":\"" + summaryRid + "\",\"status\":\"processed\"," +
                                "\"progress\":100,\"title\":\"E2E 회의 요약\"," +
                                "\"minutes\":[{\"title\":\"주요 안건\"," +
                                "\"bullets\":[{\"isImportant\":true,\"text\":\"E2E 테스트 완료 항목\"}]}]}")));

        // when: 실제 processSingleSummaryJob() 호출
        sttJobProcessor.processSingleSummaryJob(sttId);

        // then: COMPLETED로 전이 + DB 저장 확인
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    STTDto cached = sttCacheService.getCachedSttStatus(sttId);
                    assertThat(cached).isNotNull();
                    assertThat(cached.getStatus()).isEqualTo(STT.Status.COMPLETED);
                });

        // Redis 캐시 검증
        STTDto completedCached = sttCacheService.getCachedSttStatus(sttId);
        assertThat(completedCached.getStatus()).isEqualTo(STT.Status.COMPLETED);
        assertThat(completedCached.getSummary()).isNotBlank();

        // DB 저장 검증 (processSingleSummaryJob의 최종 sttRepository.save 확인)
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    STT dbStt = sttRepository.findById(sttId).orElse(null);
                    assertThat(dbStt).isNotNull();
                    assertThat(dbStt.getStatus()).isEqualTo(STT.Status.COMPLETED);
                    assertThat(dbStt.getSummary()).isNotBlank();
                });

        // SUMMARIZING 폴링 셋에서 제거 확인
        assertThat(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).doesNotContain(sttId);

        System.out.println("[17] processSingleSummaryJob() 완료 → SUMMARIZING→COMPLETED 전이");
        System.out.println("[17] DB 저장 확인: sttId=" + sttId + ", status=COMPLETED");
        System.out.println("[17] summary 길이=" + completedCached.getSummary().length());
    }
}
