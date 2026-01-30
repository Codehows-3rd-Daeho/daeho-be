package com.codehows.daehobe.stt.performance;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STT 실제 DB 기반 성능 테스트
 *
 * 테스트 환경:
 * - MySQL 8.0 (Testcontainers)
 * - Redis 7 (Testcontainers)
 * - 100만 건 더미 데이터
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("STT 실제 DB 성능 테스트 (MySQL + 100만 건)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SttRealDbPerformanceTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--max_allowed_packet=256M", "--innodb_buffer_pool_size=512M");

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // JPA 설정
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.properties.hibernate.jdbc.batch_size", () -> "1000");
        registry.add("spring.jpa.properties.hibernate.order_inserts", () -> "true");

        // Redis 설정
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // STT Polling 설정
        registry.add("stt.polling.interval-ms", () -> "100");
        registry.add("stt.polling.max-attempts", () -> "10");
    }

    @Autowired
    private STTRepository sttRepository;

    @Autowired
    private SttCacheService sttCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 성능 결과 저장
    private static final Map<String, PerformanceResult> results = new LinkedHashMap<>();
    private static boolean dataInserted = false;
    private static final int TOTAL_RECORDS = 1_000_000;  // 100만 건
    private static final int PROCESSING_COUNT = 100;      // PROCESSING 상태 레코드 수
    private static final int SUMMARIZING_COUNT = 50;      // SUMMARIZING 상태 레코드 수

    @BeforeEach
    void setUp() {
        // Redis 초기화
        Set<Long> keys = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        for (Long id : keys) {
            sttCacheService.removeFromPollingSet(id, STT.Status.PROCESSING);
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 100만 건 더미 데이터 삽입")
    void insertMillionRecords() {
        if (dataInserted) {
            System.out.println("데이터가 이미 삽입되어 있습니다.");
            return;
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  100만 건 더미 데이터 삽입 시작...");
        System.out.println("=".repeat(70));

        // 기존 STT 테이블 삭제 후 FK 없는 테이블 재생성
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS stt");
            jdbcTemplate.execute("""
                CREATE TABLE stt (
                    stt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    meeting_id BIGINT,
                    rid VARCHAR(255),
                    summary_rid VARCHAR(255),
                    content LONGTEXT,
                    summary TEXT,
                    status VARCHAR(50),
                    chunking_cnt INT DEFAULT 0,
                    retry_count INT DEFAULT 0,
                    created_at DATETIME,
                    updated_at DATETIME,
                    created_by BIGINT,
                    updated_by BIGINT,
                    INDEX idx_status_retry (status, retry_count)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (Exception e) {
            System.out.println("테이블 재생성 중 오류 (무시): " + e.getMessage());
        }

        long startTime = System.currentTimeMillis();

        int batchSize = 10000;  // 1만 건씩 배치 삽입
        int insertedCount = 0;

        for (int batch = 0; batch < TOTAL_RECORDS / batchSize; batch++) {
            StringBuilder batchSql = new StringBuilder();
            batchSql.append("INSERT INTO stt (meeting_id, rid, content, summary, status, chunking_cnt, retry_count, created_at, updated_at, created_by, updated_by) VALUES ");

            List<String> values = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                int recordIndex = batch * batchSize + i;
                String status;

                // 상태 분배: 대부분 COMPLETED, 일부 PROCESSING/SUMMARIZING
                if (recordIndex < PROCESSING_COUNT) {
                    status = "PROCESSING";
                } else if (recordIndex < PROCESSING_COUNT + SUMMARIZING_COUNT) {
                    status = "SUMMARIZING";
                } else if (recordIndex < PROCESSING_COUNT + SUMMARIZING_COUNT + 1000) {
                    status = "ENCODED";
                } else {
                    status = "COMPLETED";
                }

                int retryCount = (status.equals("PROCESSING") || status.equals("SUMMARIZING"))
                        ? (recordIndex % 10)  // 0-9 사이의 retry count
                        : 0;

                values.add(String.format(
                        "(1, 'rid-%d', 'content-%d', 'summary-%d', '%s', 0, %d, NOW(), NOW(), 1, 1)",
                        recordIndex, recordIndex, recordIndex, status, retryCount
                ));
            }

            batchSql.append(String.join(",", values));

            try {
                jdbcTemplate.execute(batchSql.toString());
                insertedCount += batchSize;

                if (insertedCount % 100000 == 0) {
                    System.out.printf("  진행: %,d / %,d (%.1f%%)\n",
                            insertedCount, TOTAL_RECORDS, (insertedCount * 100.0 / TOTAL_RECORDS));
                }
            } catch (Exception e) {
                System.err.println("배치 삽입 실패: " + e.getMessage());
                throw e;
            }
        }

        long endTime = System.currentTimeMillis();
        dataInserted = true;

        System.out.printf("\n  삽입 완료: %,d 건 (%.2f초 소요)\n", insertedCount, (endTime - startTime) / 1000.0);

        // 상태별 카운트 확인
        Long totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stt", Long.class);
        Long processingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stt WHERE status = 'PROCESSING'", Long.class);
        Long summarizingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stt WHERE status = 'SUMMARIZING'", Long.class);

        System.out.printf("  총 레코드: %,d건\n", totalCount);
        System.out.printf("  PROCESSING: %,d건\n", processingCount);
        System.out.printf("  SUMMARIZING: %,d건\n", summarizingCount);
        System.out.println("=".repeat(70));

        assertThat(totalCount).isEqualTo(TOTAL_RECORDS);
    }

    @Test
    @Order(2)
    @DisplayName("2. [핵심] 폴링 대상 조회 - DB vs Redis (100만 건 환경)")
    void pollingTargetQuery_DbVsRedis_WithMillionRecords() {
        // 먼저 데이터가 있는지 확인
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stt", Long.class);
        if (count == null || count < TOTAL_RECORDS) {
            insertMillionRecords();
        }

        // Redis SET에 폴링 대상 추가 (PROCESSING 상태와 동일하게)
        for (int i = 0; i < PROCESSING_COUNT; i++) {
            sttCacheService.addToPollingSet((long) (i + 1), STT.Status.PROCESSING);
        }

        int warmupIterations = 20;
        int testIterations = 100;

        // ── 워밍업 ──
        System.out.println("\n  워밍업 중...");
        for (int i = 0; i < warmupIterations; i++) {
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        }

        // ── DB 폴링 쿼리 측정 ──
        System.out.println("  DB 폴링 쿼리 측정 중...");
        List<Long> dbLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            List<STT> results = sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            long end = System.nanoTime();
            dbLatenciesMicros.add((end - start) / 1_000);

            // 첫 번째 결과 확인
            if (i == 0) {
                System.out.printf("  DB 쿼리 결과: %d건\n", results.size());
            }
        }

        // ── Redis SET 조회 측정 ──
        System.out.println("  Redis SET 조회 측정 중...");
        List<Long> redisLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
            long end = System.nanoTime();
            redisLatenciesMicros.add((end - start) / 1_000);

            if (i == 0) {
                System.out.printf("  Redis 쿼리 결과: %d건\n", taskIds.size());
            }
        }

        PerformanceResult dbResult = calculateMetrics(dbLatenciesMicros);
        PerformanceResult redisResult = calculateMetrics(redisLatenciesMicros);

        results.put("DB 폴링 쿼리 (100만 건)", dbResult);
        results.put("Redis SET 조회 (100만 건)", redisResult);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [핵심] 폴링 대상 조회 비교 (100만 건 DB 환경)");
        System.out.println("  PROCESSING 대상: " + PROCESSING_COUNT + "건, 총 레코드: " + TOTAL_RECORDS + "건");
        System.out.println("=".repeat(70));
        printResult("DB findByStatusAndRetryCountLessThan", dbResult);
        printResult("Redis SMEMBERS", redisResult);

        double avgImprovement = (dbResult.avg - redisResult.avg) / dbResult.avg * 100;
        double p95Improvement = (dbResult.p95 - redisResult.p95) / dbResult.p95 * 100;
        System.out.printf("\n  ▶ 평균 레이턴시 개선: %.1f%% (%,.0fus → %,.0fus)\n",
                avgImprovement, dbResult.avg, redisResult.avg);
        System.out.printf("  ▶ p95 레이턴시 개선: %.1f%% (%,.0fus → %,.0fus)\n",
                p95Improvement, dbResult.p95, redisResult.p95);
        System.out.printf("  ▶ 속도 배율: %.1fx 빠름\n", dbResult.avg / redisResult.avg);
    }

    @Test
    @Order(3)
    @DisplayName("3. [핵심] 전체 폴링 사이클 - DB vs Redis (100만 건 환경)")
    void fullPollingCycle_DbVsRedis_WithMillionRecords() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stt", Long.class);
        if (count == null || count < TOTAL_RECORDS) {
            insertMillionRecords();
        }

        // Redis SET에 폴링 대상 추가 + 캐시 데이터 준비
        for (int i = 0; i < PROCESSING_COUNT; i++) {
            long sttId = i + 1;
            sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);

            STTDto dto = STTDto.builder()
                    .id(sttId)
                    .rid("rid-" + i)
                    .meetingId(1L)
                    .status(STT.Status.PROCESSING)
                    .content("content-" + i)
                    .summary("summary-" + i)
                    .chunkingCnt(0)
                    .build();
            sttCacheService.cacheSttStatus(dto);
        }

        int warmupCycles = 10;
        int testCycles = 50;

        // ── 워밍업 ──
        for (int cycle = 0; cycle < warmupCycles; cycle++) {
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        }

        // ── DB 기반 폴링 사이클 측정 ──
        // 1회 사이클: DB 쿼리 1회 + 캐시 조회 N회 + 캐시 업데이트 N회
        System.out.println("\n  DB 기반 폴링 사이클 측정 중...");
        List<Long> dbCycleLatenciesMicros = new ArrayList<>();
        for (int cycle = 0; cycle < testCycles; cycle++) {
            long start = System.nanoTime();

            // 개선 전: DB에서 폴링 대상 조회
            List<STT> tasks = sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);

            // 각 대상에 대해 캐시 조회 및 업데이트
            for (STT stt : tasks) {
                STTDto cached = sttCacheService.getCachedSttStatus(stt.getId());
                if (cached != null) {
                    cached.updateProgress(cycle);
                    sttCacheService.cacheSttStatus(cached);
                }
            }

            long end = System.nanoTime();
            dbCycleLatenciesMicros.add((end - start) / 1_000);
        }

        // ── Redis 기반 폴링 사이클 측정 ──
        System.out.println("  Redis 기반 폴링 사이클 측정 중...");
        List<Long> redisCycleLatenciesMicros = new ArrayList<>();
        for (int cycle = 0; cycle < testCycles; cycle++) {
            long start = System.nanoTime();

            // 개선 후: Redis SET에서 폴링 대상 조회
            Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

            // 각 대상에 대해 캐시 조회 및 업데이트
            for (Long sttId : taskIds) {
                STTDto cached = sttCacheService.getCachedSttStatus(sttId);
                if (cached != null) {
                    cached.updateProgress(cycle);
                    sttCacheService.cacheSttStatus(cached);
                }
            }

            long end = System.nanoTime();
            redisCycleLatenciesMicros.add((end - start) / 1_000);
        }

        PerformanceResult dbResult = calculateMetrics(dbCycleLatenciesMicros);
        PerformanceResult redisResult = calculateMetrics(redisCycleLatenciesMicros);

        results.put("폴링 사이클 - DB (100만 건)", dbResult);
        results.put("폴링 사이클 - Redis (100만 건)", redisResult);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [핵심] 전체 폴링 사이클 비교 (100만 건 DB 환경)");
        System.out.println("  1 사이클 = 대상 조회 + 캐시 조회/업데이트 " + PROCESSING_COUNT + "회");
        System.out.println("=".repeat(70));
        printResult("DB 기반 (개선 전)", dbResult);
        printResult("Redis 기반 (개선 후)", redisResult);

        double avgImprovement = (dbResult.avg - redisResult.avg) / dbResult.avg * 100;
        double p95Improvement = (dbResult.p95 - redisResult.p95) / dbResult.p95 * 100;
        System.out.printf("\n  ▶ 사이클당 평균 개선: %.1f%% (%,.0fus → %,.0fus)\n",
                avgImprovement, dbResult.avg, redisResult.avg);
        System.out.printf("  ▶ 사이클당 p95 개선: %.1f%% (%,.0fus → %,.0fus)\n",
                p95Improvement, dbResult.p95, redisResult.p95);
        System.out.printf("  ▶ 속도 배율: %.1fx 빠름\n", dbResult.avg / redisResult.avg);

        // 2초 폴링 기준 예상 절감 계산
        double savedTimePerPoll = dbResult.avg - redisResult.avg;  // 마이크로초
        double pollsPerMinute = 30;  // 2초마다 1회
        double savedTimePerMinute = savedTimePerPoll * pollsPerMinute / 1000;  // 밀리초
        System.out.printf("\n  ▶ 분당 예상 절감 시간: %.2fms (폴링 30회 기준)\n", savedTimePerMinute);
    }

    @Test
    @Order(4)
    @DisplayName("4. 인덱스 활용 확인 (EXPLAIN)")
    void verifyIndexUsage() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stt", Long.class);
        if (count == null || count < TOTAL_RECORDS) {
            insertMillionRecords();
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  폴링 쿼리 실행 계획 (EXPLAIN)");
        System.out.println("=".repeat(70));

        // 인덱스 확인
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM stt");
        System.out.println("\n  현재 인덱스:");
        for (Map<String, Object> index : indexes) {
            System.out.printf("    - %s: %s\n", index.get("Key_name"), index.get("Column_name"));
        }

        // EXPLAIN 실행
        List<Map<String, Object>> explain = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM stt WHERE status = 'PROCESSING' AND retry_count < 150"
        );
        System.out.println("\n  실행 계획:");
        for (Map<String, Object> row : explain) {
            System.out.printf("    type: %s, key: %s, rows: %s, Extra: %s\n",
                    row.get("type"), row.get("key"), row.get("rows"), row.get("Extra"));
        }
    }

    @AfterAll
    static void printFinalResults() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(90)).append("\n");
        sb.append("              STT 실제 DB 성능 테스트 결과 (MySQL 8.0 + 100만 건)\n");
        sb.append("=".repeat(90)).append("\n");

        sb.append(String.format("\n%-45s %12s %12s %12s %12s\n",
                "테스트 항목", "avg(us)", "p50(us)", "p95(us)", "p99(us)"));
        sb.append("-".repeat(90)).append("\n");

        results.forEach((name, result) -> {
            sb.append(String.format("%-45s %,12.0f %,12.0f %,12.0f %,12.0f\n",
                    name, result.avg, result.p50, result.p95, result.p99));
        });

        sb.append("=".repeat(90)).append("\n");

        System.out.println(sb.toString());

        // 파일로 저장
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("build/performance-results-mysql.txt"),
                sb.toString()
        );
    }

    private PerformanceResult calculateMetrics(List<Long> latenciesMicros) {
        if (latenciesMicros.isEmpty()) {
            return new PerformanceResult();
        }

        Collections.sort(latenciesMicros);
        int size = latenciesMicros.size();

        PerformanceResult result = new PerformanceResult();
        result.p50 = latenciesMicros.get((int) (size * 0.50));
        result.p90 = latenciesMicros.get((int) (size * 0.90));
        result.p95 = latenciesMicros.get(Math.min((int) (size * 0.95), size - 1));
        result.p99 = latenciesMicros.get(Math.min((int) (size * 0.99), size - 1));
        result.avg = latenciesMicros.stream().mapToLong(Long::longValue).average().orElse(0);
        result.min = latenciesMicros.get(0);
        result.max = latenciesMicros.get(size - 1);

        return result;
    }

    private void printResult(String name, PerformanceResult result) {
        System.out.printf("  %-40s avg=%,10.0fus  p50=%,8.0fus  p95=%,8.0fus  p99=%,8.0fus\n",
                name, result.avg, result.p50, result.p95, result.p99);
    }

    static class PerformanceResult {
        double avg = 0;
        double p50 = 0;
        double p90 = 0;
        double p95 = 0;
        double p99 = 0;
        long min = 0;
        long max = 0;
    }
}
