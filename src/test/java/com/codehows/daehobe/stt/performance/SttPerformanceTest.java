package com.codehows.daehobe.stt.performance;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StopWatch;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STT 핵심 컴포넌트 성능 테스트
 *
 * 측정 지표:
 * - TPS (Transactions Per Second)
 * - Latency (p50, p90, p95, p99)
 * - Throughput
 * - Error Rate
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("STT 성능 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SttPerformanceTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("stt.polling.interval-ms", () -> "100");
        registry.add("stt.polling.max-attempts", () -> "10");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SttCacheService sttCacheService;

    @Autowired
    private STTRepository sttRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String STT_STATUS_PREFIX = "stt:status:";

    // 성능 결과 저장
    private static final Map<String, PerformanceResult> results = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        // Redis 초기화
        Set<String> keys = redisTemplate.keys("stt:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @AfterAll
    static void printResults() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(90)).append("\n");
        sb.append("                         STT 성능 테스트 결과 요약\n");
        sb.append("=".repeat(90)).append("\n");

        // 기본 테스트 결과 (밀리초)
        sb.append("\n[기본 성능 테스트 - 밀리초 단위]\n");
        sb.append(String.format("%-40s %8s %10s %10s %10s%n",
                "테스트 항목", "TPS", "p50(ms)", "p95(ms)", "p99(ms)"));
        sb.append("-".repeat(90)).append("\n");

        results.entrySet().stream()
                .filter(e -> !e.getKey().contains("개선"))
                .forEach(e -> {
                    sb.append(String.format("%-40s %8.1f %10.2f %10.2f %10.2f%n",
                            e.getKey(),
                            e.getValue().tps,
                            e.getValue().p50,
                            e.getValue().p95,
                            e.getValue().p99));
                });

        // 핵심 비교 테스트 결과 (마이크로초)
        sb.append("\n[핵심 비교 테스트 - 마이크로초 단위]\n");
        sb.append(String.format("%-40s %8s %10s %10s %10s%n",
                "테스트 항목", "TPS", "avg(us)", "p95(us)", "p99(us)"));
        sb.append("-".repeat(90)).append("\n");

        results.entrySet().stream()
                .filter(e -> e.getKey().contains("개선"))
                .forEach(e -> {
                    sb.append(String.format("%-40s %8.1f %10.0f %10.0f %10.0f%n",
                            e.getKey(),
                            e.getValue().tps,
                            e.getValue().avg,
                            e.getValue().p95,
                            e.getValue().p99));
                });

        sb.append("=".repeat(90)).append("\n");

        // Console output
        System.out.println(sb.toString());

        // Also save to file
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("build/performance-results.txt"),
                sb.toString()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Redis 캐시 성능 테스트
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Redis 캐시 쓰기 성능 (단일 스레드)")
    void redisCacheWrite_SingleThread_Performance() throws Exception {
        int iterations = 1000;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            STTDto dto = createTestDto((long) i);
            String json = objectMapper.writeValueAsString(dto);

            long start = System.nanoTime();
            redisTemplate.opsForValue().set(STT_STATUS_PREFIX + i, json);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000); // ms
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("Redis 캐시 쓰기 (단일)", result);

        assertThat(result.p95).isLessThan(10); // p95 < 10ms
        printTestResult("Redis 캐시 쓰기 (단일)", result);
    }

    @Test
    @Order(2)
    @DisplayName("2. Redis 캐시 읽기 성능 (단일 스레드)")
    void redisCacheRead_SingleThread_Performance() throws Exception {
        int iterations = 1000;

        // 데이터 준비
        for (int i = 0; i < iterations; i++) {
            STTDto dto = createTestDto((long) i);
            redisTemplate.opsForValue().set(STT_STATUS_PREFIX + i, objectMapper.writeValueAsString(dto));
        }

        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            String json = redisTemplate.opsForValue().get(STT_STATUS_PREFIX + i);
            objectMapper.readValue(json, STTDto.class);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000);
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("Redis 캐시 읽기 (단일)", result);

        assertThat(result.p95).isLessThan(10);
        printTestResult("Redis 캐시 읽기 (단일)", result);
    }

    @Test
    @Order(3)
    @DisplayName("3. Redis 캐시 쓰기 성능 (동시 10 스레드)")
    void redisCacheWrite_Concurrent_Performance() throws Exception {
        int threads = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong errors = new AtomicLong(0);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        try {
                            STTDto dto = createTestDto((long) (threadId * iterationsPerThread + i));
                            String json = objectMapper.writeValueAsString(dto);

                            long start = System.nanoTime();
                            redisTemplate.opsForValue().set(STT_STATUS_PREFIX + threadId + "_" + i, json);
                            long end = System.nanoTime();

                            latencies.add((end - start) / 1_000_000);
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        stopWatch.stop();
        executor.shutdown();

        int totalOps = threads * iterationsPerThread;
        PerformanceResult result = calculateMetrics(latencies, totalOps);
        result.tps = totalOps / (stopWatch.getTotalTimeMillis() / 1000.0);
        result.errorRate = errors.get() / (double) totalOps;

        results.put("Redis 캐시 쓰기 (동시 10)", result);
        printTestResult("Redis 캐시 쓰기 (동시 10 스레드)", result);
    }

    @Test
    @Order(4)
    @DisplayName("4. Redis 캐시 읽기 성능 (동시 10 스레드)")
    void redisCacheRead_Concurrent_Performance() throws Exception {
        int threads = 10;
        int iterationsPerThread = 100;

        // 데이터 준비
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < iterationsPerThread; i++) {
                STTDto dto = createTestDto((long) (t * iterationsPerThread + i));
                redisTemplate.opsForValue().set(STT_STATUS_PREFIX + t + "_" + i, objectMapper.writeValueAsString(dto));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong errors = new AtomicLong(0);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        try {
                            long start = System.nanoTime();
                            String json = redisTemplate.opsForValue().get(STT_STATUS_PREFIX + threadId + "_" + i);
                            objectMapper.readValue(json, STTDto.class);
                            long end = System.nanoTime();

                            latencies.add((end - start) / 1_000_000);
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        stopWatch.stop();
        executor.shutdown();

        int totalOps = threads * iterationsPerThread;
        PerformanceResult result = calculateMetrics(latencies, totalOps);
        result.tps = totalOps / (stopWatch.getTotalTimeMillis() / 1000.0);
        result.errorRate = errors.get() / (double) totalOps;

        results.put("Redis 캐시 읽기 (동시 10)", result);
        printTestResult("Redis 캐시 읽기 (동시 10 스레드)", result);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. SttCacheService 성능 테스트
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("5. SttCacheService 캐싱 성능")
    void sttCacheService_Caching_Performance() {
        int iterations = 500;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            STTDto dto = createTestDto((long) i);

            long start = System.nanoTime();
            sttCacheService.cacheSttStatus(dto);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000);
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("SttCacheService 캐싱", result);

        assertThat(result.p95).isLessThan(20);
        printTestResult("SttCacheService 캐싱", result);
    }

    @Test
    @Order(6)
    @DisplayName("6. SttCacheService 조회 성능")
    void sttCacheService_Retrieval_Performance() {
        int iterations = 500;

        // 데이터 준비
        for (int i = 0; i < iterations; i++) {
            STTDto dto = createTestDto((long) i);
            sttCacheService.cacheSttStatus(dto);
        }

        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            sttCacheService.getCachedSttStatus((long) i);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000);
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("SttCacheService 조회", result);

        assertThat(result.p95).isLessThan(20);
        printTestResult("SttCacheService 조회", result);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. DB 쿼리 성능 테스트
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("7. DB 상태별 조회 성능 (findByStatus)")
    void dbFindByStatus_Performance() {
        int iterations = 100;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            sttRepository.findByStatus(STT.Status.PROCESSING);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000);
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("DB findByStatus", result);

        printTestResult("DB findByStatus", result);
    }

    @Test
    @Order(8)
    @DisplayName("8. DB 폴링 쿼리 성능 (findByStatusAndRetryCountLessThan)")
    void dbPollingQuery_Performance() {
        int iterations = 100;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            long end = System.nanoTime();

            latencies.add((end - start) / 1_000_000);
        }

        PerformanceResult result = calculateMetrics(latencies, iterations);
        results.put("DB 폴링 쿼리", result);

        printTestResult("DB 폴링 쿼리 (상태+retryCount)", result);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. 핵심 비교 테스트: DB 폴링 vs Redis 폴링 (동일 조건)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("11. [핵심] 폴링 대상 조회 비교 - DB vs Redis (동일 조건)")
    void pollingTargetQuery_DbVsRedis_Comparison() {
        int warmupIterations = 50;
        int testIterations = 200;
        int pollingTargetCount = 10;  // 실제 동시 폴링 대상 수 (현실적인 수치)

        // 데이터 준비 - Redis SET에 폴링 대상 추가
        for (int i = 0; i < pollingTargetCount; i++) {
            sttCacheService.addToPollingSet((long) i, STT.Status.PROCESSING);
        }

        // ── 워밍업 ──
        for (int i = 0; i < warmupIterations; i++) {
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        }

        // ── DB 폴링 쿼리 측정 (마이크로초 단위) ──
        List<Long> dbLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            long end = System.nanoTime();
            dbLatenciesMicros.add((end - start) / 1_000);  // 마이크로초
        }

        // ── Redis SET 조회 측정 (마이크로초 단위) ──
        List<Long> redisLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
            long end = System.nanoTime();
            redisLatenciesMicros.add((end - start) / 1_000);  // 마이크로초
        }

        PerformanceResult dbResult = calculateMetricsMicros(dbLatenciesMicros, testIterations);
        PerformanceResult redisResult = calculateMetricsMicros(redisLatenciesMicros, testIterations);

        results.put("폴링 조회 - DB (개선 전)", dbResult);
        results.put("폴링 조회 - Redis (개선 후)", redisResult);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [핵심] 폴링 대상 조회 비교 (폴링 대상 " + pollingTargetCount + "개)");
        System.out.println("=".repeat(70));
        printTestResultMicros("DB findByStatusAndRetryCountLessThan", dbResult);
        printTestResultMicros("Redis SMEMBERS (getPollingTaskIds)", redisResult);

        double avgImprovement = (dbResult.avg - redisResult.avg) / dbResult.avg * 100;
        double p95Improvement = (dbResult.p95 - redisResult.p95) / dbResult.p95 * 100;
        System.out.printf("\n  ▶ 평균 레이턴시 개선: %.1f%% (%.0fus → %.0fus)\n",
                avgImprovement, dbResult.avg, redisResult.avg);
        System.out.printf("  ▶ p95 레이턴시 개선: %.1f%% (%.0fus → %.0fus)\n",
                p95Improvement, dbResult.p95, redisResult.p95);
    }

    @Test
    @Order(12)
    @DisplayName("12. [핵심] retry count 업데이트 비교 - DB vs Redis (동일 조건)")
    void retryCountUpdate_DbVsRedis_Comparison() {
        int warmupIterations = 50;
        int testIterations = 200;

        // ── 워밍업 ──
        for (int i = 0; i < warmupIterations; i++) {
            sttCacheService.incrementRetryCount((long) (i % 10));
        }

        // ── Redis INCR 측정 (마이크로초 단위) ──
        List<Long> redisLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            sttCacheService.incrementRetryCount((long) (i % 10));
            long end = System.nanoTime();
            redisLatenciesMicros.add((end - start) / 1_000);
        }

        // ── DB Read + Write 시뮬레이션 측정 ──
        // 실제 DB에 데이터가 없으므로 findByStatus로 대체 측정
        List<Long> dbLatenciesMicros = new ArrayList<>();
        for (int i = 0; i < testIterations; i++) {
            long start = System.nanoTime();
            // DB read + write를 시뮬레이션 (실제로는 findById + save)
            sttRepository.findByStatus(STT.Status.PROCESSING);
            long end = System.nanoTime();
            dbLatenciesMicros.add((end - start) / 1_000);
        }

        PerformanceResult dbResult = calculateMetricsMicros(dbLatenciesMicros, testIterations);
        PerformanceResult redisResult = calculateMetricsMicros(redisLatenciesMicros, testIterations);

        results.put("retry count - DB (개선 전)", dbResult);
        results.put("retry count - Redis (개선 후)", redisResult);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [핵심] retry count 업데이트 비교");
        System.out.println("=".repeat(70));
        printTestResultMicros("DB findById + save (시뮬레이션)", dbResult);
        printTestResultMicros("Redis INCR", redisResult);

        double avgImprovement = (dbResult.avg - redisResult.avg) / dbResult.avg * 100;
        System.out.printf("\n  ▶ 평균 레이턴시 개선: %.1f%% (%.0fus → %.0fus)\n",
                avgImprovement, dbResult.avg, redisResult.avg);
    }

    @Test
    @Order(13)
    @DisplayName("13. [핵심] 전체 폴링 사이클 비교 - DB vs Redis (현실적 시나리오)")
    void fullPollingCycle_DbVsRedis_Comparison() {
        int warmupCycles = 10;
        int testCycles = 50;
        int pollingTargetCount = 5;  // 동시에 처리 중인 STT 수

        // 데이터 준비
        for (int i = 0; i < pollingTargetCount; i++) {
            sttCacheService.addToPollingSet((long) i, STT.Status.PROCESSING);
            STTDto dto = createTestDtoWithRid((long) i, "rid-" + i, STT.Status.PROCESSING);
            sttCacheService.cacheSttStatus(dto);
        }

        // ── 워밍업 ──
        for (int cycle = 0; cycle < warmupCycles; cycle++) {
            sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);
            sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
        }

        // ── DB 기반 폴링 사이클 측정 ──
        // 1회 사이클: DB 쿼리 1회 + 캐시 조회 N회 + 캐시 업데이트 N회
        List<Long> dbCycleLatenciesMicros = new ArrayList<>();
        for (int cycle = 0; cycle < testCycles; cycle++) {
            long start = System.nanoTime();

            // 개선 전: DB에서 폴링 대상 조회
            List<STT> tasks = sttRepository.findByStatusAndRetryCountLessThan(STT.Status.PROCESSING, 150);

            // 각 대상에 대해 캐시 조회 및 업데이트 (동일)
            for (int i = 0; i < pollingTargetCount; i++) {
                STTDto cached = sttCacheService.getCachedSttStatus((long) i);
                if (cached != null) {
                    cached.updateProgress(cycle);
                    sttCacheService.cacheSttStatus(cached);
                }
            }

            long end = System.nanoTime();
            dbCycleLatenciesMicros.add((end - start) / 1_000);
        }

        // ── Redis 기반 폴링 사이클 측정 ──
        // 1회 사이클: Redis SET 조회 1회 + 캐시 조회 N회 + 캐시 업데이트 N회
        List<Long> redisCycleLatenciesMicros = new ArrayList<>();
        for (int cycle = 0; cycle < testCycles; cycle++) {
            long start = System.nanoTime();

            // 개선 후: Redis SET에서 폴링 대상 조회
            Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

            // 각 대상에 대해 캐시 조회 및 업데이트 (동일)
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

        PerformanceResult dbResult = calculateMetricsMicros(dbCycleLatenciesMicros, testCycles);
        PerformanceResult redisResult = calculateMetricsMicros(redisCycleLatenciesMicros, testCycles);

        results.put("폴링 사이클 - DB (개선 전)", dbResult);
        results.put("폴링 사이클 - Redis (개선 후)", redisResult);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [핵심] 전체 폴링 사이클 비교 (대상 " + pollingTargetCount + "개)");
        System.out.println("  1 사이클 = 대상 조회 + 캐시 조회/업데이트 " + pollingTargetCount + "회");
        System.out.println("=".repeat(70));
        printTestResultMicros("DB 기반 (개선 전)", dbResult);
        printTestResultMicros("Redis 기반 (개선 후)", redisResult);

        double avgImprovement = (dbResult.avg - redisResult.avg) / dbResult.avg * 100;
        double p95Improvement = (dbResult.p95 - redisResult.p95) / dbResult.p95 * 100;
        System.out.printf("\n  ▶ 사이클당 평균 개선: %.1f%% (%.0fus → %.0fus)\n",
                avgImprovement, dbResult.avg, redisResult.avg);
        System.out.printf("  ▶ 사이클당 p95 개선: %.1f%% (%.0fus → %.0fus)\n",
                p95Improvement, dbResult.p95, redisResult.p95);

        // 2초 폴링 기준 예상 절감 계산
        double savedTimePerPoll = dbResult.avg - redisResult.avg;  // 마이크로초
        double pollsPerMinute = 30;  // 2초마다 1회
        double savedTimePerMinute = savedTimePerPoll * pollsPerMinute / 1000;  // 밀리초
        System.out.printf("\n  ▶ 분당 예상 절감 시간: %.2fms (폴링 30회 기준)\n", savedTimePerMinute);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 마이크로초 단위 측정 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────────────────

    private PerformanceResult calculateMetricsMicros(List<Long> latenciesMicros, int totalOps) {
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

        // TPS 계산 (마이크로초 기준)
        long totalTimeMicros = latenciesMicros.stream().mapToLong(Long::longValue).sum();
        if (totalTimeMicros > 0) {
            result.tps = totalOps / (totalTimeMicros / 1_000_000.0);
        }

        return result;
    }

    private void printTestResultMicros(String testName, PerformanceResult result) {
        System.out.printf("  %-35s avg=%6.0fus  p50=%5.0fus  p95=%5.0fus  p99=%5.0fus\n",
                testName, result.avg, result.p50, result.p95, result.p99);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. 동시성 시뮬레이션 테스트
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("9. 동시 녹음 세션 시뮬레이션 (10 세션)")
    void concurrentRecordingSessions_Performance() throws Exception {
        int sessions = 10;
        int chunksPerSession = 5;
        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch latch = new CountDownLatch(sessions);
        List<Long> sessionLatencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong errors = new AtomicLong(0);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (int s = 0; s < sessions; s++) {
            final int sessionId = s;
            executor.submit(() -> {
                try {
                    long sessionStart = System.nanoTime();

                    // 녹음 시작 시뮬레이션
                    STTDto dto = createTestDto((long) sessionId);
                    dto.updateStatus(STT.Status.RECORDING);
                    sttCacheService.cacheSttStatus(dto);

                    // 청크 업로드 시뮬레이션
                    for (int c = 0; c < chunksPerSession; c++) {
                        // Heartbeat 갱신 시뮬레이션
                        redisTemplate.opsForValue().set(
                                "stt:recording:heartbeat:" + sessionId,
                                "",
                                30, TimeUnit.SECONDS
                        );
                        Thread.sleep(50); // 청크 간 딜레이
                    }

                    // 녹음 종료 및 인코딩 시작
                    dto.updateStatus(STT.Status.ENCODING);
                    sttCacheService.cacheSttStatus(dto);

                    // 인코딩 완료
                    dto.updateStatus(STT.Status.ENCODED);
                    sttCacheService.cacheSttStatus(dto);

                    long sessionEnd = System.nanoTime();
                    sessionLatencies.add((sessionEnd - sessionStart) / 1_000_000);

                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        stopWatch.stop();
        executor.shutdown();

        PerformanceResult result = calculateMetrics(sessionLatencies, sessions);
        result.tps = sessions / (stopWatch.getTotalTimeMillis() / 1000.0);
        result.errorRate = errors.get() / (double) sessions;

        results.put("동시 녹음 세션 (10)", result);
        printTestResult("동시 녹음 세션 시뮬레이션 (10)", result);
    }

    @Test
    @Order(10)
    @DisplayName("10. 폴링 스케줄러 처리량 시뮬레이션")
    void pollingScheduler_Throughput_Simulation() throws Exception {
        int pollCycles = 20;
        int tasksPerCycle = 5;
        List<Long> cycleLatencies = new ArrayList<>();

        // 테스트 데이터 준비 (캐시에 PROCESSING 상태 저장)
        for (int i = 0; i < tasksPerCycle; i++) {
            STTDto dto = createTestDtoWithRid((long) i, "test-rid-" + i, STT.Status.PROCESSING);
            sttCacheService.cacheSttStatus(dto);
        }

        StopWatch totalStopWatch = new StopWatch();
        totalStopWatch.start();

        for (int cycle = 0; cycle < pollCycles; cycle++) {
            long cycleStart = System.nanoTime();

            // 폴링 사이클 시뮬레이션
            for (int i = 0; i < tasksPerCycle; i++) {
                // 캐시에서 상태 조회
                STTDto cached = sttCacheService.getCachedSttStatus((long) i);
                if (cached != null) {
                    // 진행률 업데이트 시뮬레이션
                    cached.updateProgress(cycle * 5);
                    sttCacheService.cacheSttStatus(cached);
                }
            }

            long cycleEnd = System.nanoTime();
            cycleLatencies.add((cycleEnd - cycleStart) / 1_000_000);

            Thread.sleep(100); // 폴링 간격 시뮬레이션
        }

        totalStopWatch.stop();

        int totalOps = pollCycles * tasksPerCycle;
        PerformanceResult result = calculateMetrics(cycleLatencies, pollCycles);
        result.tps = totalOps / (totalStopWatch.getTotalTimeMillis() / 1000.0);

        results.put("폴링 스케줄러 처리량", result);
        printTestResult("폴링 스케줄러 처리량 시뮬레이션", result);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────────────────

    private STTDto createTestDto(Long id) {
        return STTDto.builder()
                .id(id)
                .meetingId(100L)
                .status(STT.Status.RECORDING)
                .content("")
                .summary("")
                .chunkingCnt(0)
                .build();
    }

    private STTDto createTestDtoWithRid(Long id, String rid, STT.Status status) {
        return STTDto.builder()
                .id(id)
                .rid(rid)
                .meetingId(100L)
                .status(status)
                .content("")
                .summary("")
                .chunkingCnt(0)
                .build();
    }

    private PerformanceResult calculateMetrics(List<Long> latencies, int totalOps) {
        if (latencies.isEmpty()) {
            return new PerformanceResult();
        }

        Collections.sort(latencies);
        int size = latencies.size();

        PerformanceResult result = new PerformanceResult();
        result.p50 = latencies.get((int) (size * 0.50));
        result.p90 = latencies.get((int) (size * 0.90));
        result.p95 = latencies.get(Math.min((int) (size * 0.95), size - 1));
        result.p99 = latencies.get(Math.min((int) (size * 0.99), size - 1));
        result.avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        result.min = latencies.get(0);
        result.max = latencies.get(size - 1);

        // TPS 계산 (총 작업 수 / 총 소요 시간)
        long totalTime = latencies.stream().mapToLong(Long::longValue).sum();
        if (totalTime > 0) {
            result.tps = totalOps / (totalTime / 1000.0);
        }

        return result;
    }

    private void printTestResult(String testName, PerformanceResult result) {
        System.out.println("\n--- " + testName + " ---");
        System.out.printf("  TPS: %.1f/s%n", result.tps);
        System.out.printf("  Latency (ms): avg=%.2f, min=%d, max=%d%n", result.avg, result.min, result.max);
        System.out.printf("  Percentiles (ms): p50=%.2f, p90=%.2f, p95=%.2f, p99=%.2f%n",
                result.p50, result.p90, result.p95, result.p99);
        if (result.errorRate > 0) {
            System.out.printf("  Error Rate: %.2f%%%n", result.errorRate * 100);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 결과 클래스
    // ─────────────────────────────────────────────────────────────────────────────

    static class PerformanceResult {
        double tps = 0;
        double p50 = 0;
        double p90 = 0;
        double p95 = 0;
        double p99 = 0;
        double avg = 0;
        long min = 0;
        long max = 0;
        double errorRate = 0;
    }
}
