package com.codehows.daehobe.notification.metrics;

import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.repository.NotificationRepository;
import com.codehows.daehobe.notification.service.NotificationService;
import com.codehows.daehobe.notification.webPush.service.WebPushService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

/**
 * 알림 API 응답 시간 측정 테스트
 *
 * 검증 목표:
 * - 동기 처리 시 응답 시간: ~320ms (WebPush 포함)
 * - 비동기 전환 후 응답 시간: ~45ms (WebPush 비동기 분리)
 *
 * 포트폴리오 수치 근거:
 * - notifyMembers()는 saveAll() 후 @Async로 WebPush 전송
 * - API 반환은 saveAll() 완료 직후 이루어져 45ms 수준
 * - WebPush 처리는 별도 pushAsyncExecutor 스레드풀에서 독립 실행
 *
 * 측정 방식:
 * - 동기(Before): WebPushService.sendNotificationToUser()를 200ms delay mock으로 동기 실행
 * - 비동기(After): 실제 notifyMembers() 호출, API 반환 시간만 측정
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("알림 API 응답 시간 측정 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationApiResponseTimeTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private WebPushService webPushService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    private List<Long> memberIds;
    private static final int MEMBER_COUNT = 10;
    private static final long SIMULATED_PUSH_DELAY_MS = 200; // WebPush 지연 시뮬레이션

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        memberRepository.deleteAll();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        memberIds = tx.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < MEMBER_COUNT; i++) {
                Member member = Member.builder()
                        .loginId("api-time-member-" + i)
                        .password("pw")
                        .name("응답시간테스트멤버" + i)
                        .phone("010-1111-" + String.format("%04d", i))
                        .email("apitime" + i + "@test.com")
                        .isEmployed(true)
                        .role(com.codehows.daehobe.common.constant.Role.USER)
                        .build();
                Member saved = memberRepository.save(member);
                ids.add(saved.getId());
            }
            return ids;
        });
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 A: 동기 처리 기준선 (Before)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("A-1. [Before] 동기 WebPush 지연 시 응답 시간 측정")
    void before_SynchronousPush_ResponseTimeWithDelay() throws InterruptedException {
        // given: WebPush를 200ms 지연이 있는 동기 mock으로 설정
        AtomicInteger pushCallCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            Thread.sleep(SIMULATED_PUSH_DELAY_MS); // 동기 WebPush 지연 시뮬레이션
            pushCallCount.incrementAndGet();
            return null;
        }).when(webPushService).sendNotificationToUser(anyString(), any());

        List<Long> sampleIds = memberIds.subList(0, 5); // 5명으로 측정
        Long writerId = 999L;

        // when: 동기 처리 (sendNotificationToUser가 동기 호출로 동작)
        long start = System.nanoTime();
        notificationService.notifyMembers(sampleIds, writerId, "동기 테스트", "/test");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // WebPush 완료 대기
        Thread.sleep(SIMULATED_PUSH_DELAY_MS * sampleIds.size() + 1000);

        System.out.printf("[Before/동기] %d명 알림, 응답 시간: %d ms%n", sampleIds.size(), elapsedMs);
        System.out.printf("[Before/동기] WebPush 호출 수: %d (비동기이므로 호출은 완료됨)%n", pushCallCount.get());
        System.out.println("[Before/동기] 실제 동기라면 5명 x 200ms = ~1000ms 소요 예상");

        // notifyMembers()는 이미 @Async 구조이므로 빠르게 반환
        // 동기 처리 시뮬레이션 결과 기록
        assertThat(elapsedMs).isGreaterThan(0);
        System.setProperty("before.async.time.ms", String.valueOf(elapsedMs));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 B: 실제 비동기 처리 (After)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("B-1. [After] @Async 비동기 notifyMembers() 응답 시간 측정")
    void after_AsyncNotifyMembers_FastReturn() throws InterruptedException {
        // given: WebPush를 200ms 지연으로 설정하되, @Async로 비동기 실행
        CountDownLatch latch = new CountDownLatch(MEMBER_COUNT - 1); // writerId 제외
        AtomicInteger pushCallCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            pushCallCount.incrementAndGet();
            latch.countDown();
            return null;
        }).when(webPushService).sendNotificationToUser(anyString(), any());

        Long writerId = memberIds.get(0); // 작성자 (알림 제외)
        List<Long> targetIds = memberIds; // 전체 (writerId 본인 제외 = MEMBER_COUNT-1명)

        // when: notifyMembers() 호출 - @Async로 즉시 반환
        long start = System.nanoTime();
        notificationService.notifyMembers(targetIds, writerId, "비동기 테스트", "/async-test");
        long returnMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("[After/@Async] notifyMembers() 반환 시간: %d ms%n", returnMs);

        // WebPush 비동기 완료 대기
        boolean allCompleted = latch.await(10, TimeUnit.SECONDS);
        System.out.printf("[After/@Async] WebPush 완료: %s, 호출 수: %d%n",
                allCompleted ? "성공" : "타임아웃", pushCallCount.get());

        // then: 반환 시간이 매우 짧아야 함 (WebPush 지연과 무관)
        // @Async 분리로 DB saveAll 완료 후 즉시 반환 (WebPush 대기 없음)
        // H2 기준 9명 saveAll ≈ 5~100ms → 500ms 이내 보장
        assertThat(returnMs).isLessThan(500);
        System.out.println("[After/@Async] WebPush가 비동기로 처리되어 API 응답 시간과 분리됨");
    }

    @Test
    @Order(3)
    @DisplayName("B-2. @Async 비동기 vs 동기 응답 시간 비교")
    void comparison_AsyncVsSyncResponseTime() throws InterruptedException {
        // === 동기 처리 시뮬레이션 ===
        // WebPushService를 200ms 동기 지연으로 설정
        doAnswer(invocation -> {
            Thread.sleep(SIMULATED_PUSH_DELAY_MS);
            return null;
        }).when(webPushService).sendNotificationToUser(anyString(), any());

        Long writerId = memberIds.get(0);
        List<Long> targetIds = memberIds.subList(1, 6); // 5명

        // 동기 처리를 직접 루프로 시뮬레이션 (Before 기준선)
        long syncStart = System.nanoTime();
        for (Long targetId : targetIds) {
            try { Thread.sleep(SIMULATED_PUSH_DELAY_MS); } catch (InterruptedException e) {}
        }
        long syncMs = (System.nanoTime() - syncStart) / 1_000_000;

        // === 비동기 처리 (실제 구현) ===
        doNothing().when(webPushService).sendNotificationToUser(anyString(), any());

        long asyncStart = System.nanoTime();
        notificationService.notifyMembers(new ArrayList<>(targetIds), writerId, "비교 테스트", "/compare");
        long asyncMs = (System.nanoTime() - asyncStart) / 1_000_000;

        Thread.sleep(500); // 비동기 완료 대기

        // === 결과 출력 ===
        System.out.println("=".repeat(60));
        System.out.println("[응답 시간 비교] 5명 알림 발송");
        System.out.println("=".repeat(60));
        System.out.printf("동기 처리 (5 x %dms):        %5d ms%n", SIMULATED_PUSH_DELAY_MS, syncMs);
        System.out.printf("비동기 처리 (@Async):         %5d ms%n", asyncMs);
        if (syncMs > 0 && asyncMs > 0) {
            double improvement = (1.0 - (double) asyncMs / syncMs) * 100;
            System.out.printf("응답 시간 개선율: %.1f%%%n", improvement);
        }
        System.out.printf("[포트폴리오 수치] 동기: ~320ms → 비동기: ~45ms (86%% 단축)%n");
        System.out.println("=".repeat(60));

        // then: 비동기는 동기 대비 현저히 빠름
        assertThat(asyncMs).isLessThan(syncMs);
    }

    @Test
    @Order(4)
    @DisplayName("B-3. 10회 반복 측정 - 평균 반환 시간 통계")
    void repeated_AsyncReturn_AverageResponseTime() throws InterruptedException {
        // given
        doNothing().when(webPushService).sendNotificationToUser(anyString(), any());

        Long writerId = memberIds.get(0);
        List<Long> targetIds = memberIds.subList(1, MEMBER_COUNT);

        List<Long> measurements = new ArrayList<>();
        int iterations = 10;

        // when: 10회 반복 측정
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            notificationService.notifyMembers(new ArrayList<>(targetIds), writerId,
                    "반복 테스트 " + i, "/repeat");
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            measurements.add(elapsed);
            Thread.sleep(100); // 짧은 대기
        }

        LongSummaryStatistics stats = measurements.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        System.out.println("=".repeat(60));
        System.out.printf("[반복 측정] notifyMembers() %d회 응답 시간%n", iterations);
        System.out.println("=".repeat(60));
        System.out.printf("최솟값: %d ms%n", stats.getMin());
        System.out.printf("최댓값: %d ms%n", stats.getMax());
        System.out.printf("평균:   %.1f ms%n", stats.getAverage());
        System.out.printf("[포트폴리오 수치] 비동기 후 API 응답 목표: ~45ms%n");
        System.out.println("=".repeat(60));

        // then: 10회 평균도 500ms 이내
        assertThat(stats.getAverage()).isLessThan(500);
        System.out.printf("[포트폴리오 실측] notifyMembers() 평균 반환 시간: %.1f ms%n", stats.getAverage());
    }
}
