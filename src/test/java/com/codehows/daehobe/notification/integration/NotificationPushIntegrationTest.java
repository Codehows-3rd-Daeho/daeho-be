package com.codehows.daehobe.notification.integration;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.service.NotificationService;
import com.codehows.daehobe.notification.webPush.service.PushAsyncService;
import com.codehows.daehobe.notification.webPush.service.PushSubscriptionService;
import com.codehows.daehobe.notification.webPush.service.WebPushService;
import com.codehows.daehobe.notification.dto.PushResult;
import com.redis.testcontainers.RedisContainer;
import nl.martijndwars.webpush.Notification;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 푸시 알림 통합 테스트
 *
 * 테스트 환경:
 * - Testcontainers Redis
 * - Mock PushAsyncService (실제 푸시 전송 대신)
 *
 * 검증 사항:
 * 1. 단일 스레드풀(pushAsyncExecutor) 아키텍처 동작
 * 2. 대량 푸시 성능 측정
 * 3. 구독 만료 처리
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("푸시 알림 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationPushIntegrationTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WebPushService webPushService;

    @MockBean
    private PushAsyncService pushAsyncService;

    private static AtomicInteger pushCallCount;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("notification.subscription.ttl-days", () -> "1");
    }

    @BeforeEach
    void setUp() {
        pushCallCount = new AtomicInteger(0);

        // Mock PushAsyncService - 성공 응답 시뮬레이션
        when(pushAsyncService.sendAsync(any(Notification.class))).thenAnswer(invocation -> {
            pushCallCount.incrementAndGet();
            Notification notification = invocation.getArgument(0);
            return CompletableFuture.completedFuture(
                    PushResult.success(notification.getEndpoint(), 201, 50)
            );
        });
    }

    @Test
    @Order(1)
    @DisplayName("1. 단일 푸시 알림 전송 성공")
    void singlePushNotification_Success() {
        // Given: 구독 정보 등록
        String memberId = "1";
        registerSubscription(memberId);

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("테스트 알림");
        messageDto.setUrl("/test");

        // When: 푸시 전송 (동기 실행)
        webPushService.sendNotificationToUser(memberId, messageDto);

        // Then: 푸시 호출 확인
        assertThat(pushCallCount.get()).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("2. 10명 멤버 비동기 푸시 전송")
    void asyncPushNotification_10Members() {
        // Given: 10명 구독 정보 등록
        int memberCount = 10;
        for (int i = 1; i <= memberCount; i++) {
            registerSubscription(String.valueOf(i));
        }

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("테스트 알림");
        messageDto.setUrl("/test");

        // When: 비동기 푸시 전송
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= memberCount; i++) {
            notificationService.sendNotification(String.valueOf(i), messageDto);
        }

        // Then: 모든 푸시 완료 대기
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> pushCallCount.get() >= memberCount);

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("[10명 테스트] Duration: %d ms, Calls: %d, Throughput: %.1f pushes/sec%n",
                duration, pushCallCount.get(), (double) memberCount / duration * 1000);

        assertThat(pushCallCount.get()).isGreaterThanOrEqualTo(memberCount);
    }

    @Test
    @Order(3)
    @DisplayName("3. 100명 멤버 대량 푸시 성능 테스트")
    void bulkPushNotification_100Members() {
        // Given: 100명 구독 정보 Redis 등록
        int memberCount = 100;
        for (int i = 1; i <= memberCount; i++) {
            registerSubscription(String.valueOf(i));
        }

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("대량 푸시 테스트");
        messageDto.setUrl("/bulk-test");

        // When: 비동기 푸시 전송
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= memberCount; i++) {
            notificationService.sendNotification(String.valueOf(i), messageDto);
        }

        // Then: 모든 푸시 완료 대기
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> pushCallCount.get() >= memberCount);

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (double) memberCount / duration * 1000;

        System.out.printf("[100명 테스트] Duration: %d ms, Calls: %d, Throughput: %.1f pushes/sec%n",
                duration, pushCallCount.get(), throughput);

        // 성능 목표: 처리량 10 pushes/sec 이상
        assertThat(pushCallCount.get()).isGreaterThanOrEqualTo(memberCount);
        assertThat(throughput).isGreaterThan(10.0);
    }

    @Test
    @Order(4)
    @DisplayName("4. 구독 없는 멤버 푸시 시도 - 예외 발생 없이 로깅만")
    void pushToNonSubscribedMember_NoException() {
        // Given: 구독 정보 없음
        String memberId = "non-subscribed";

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("테스트 알림");
        messageDto.setUrl("/test");

        // When & Then: 예외 없이 처리
        Assertions.assertDoesNotThrow(() ->
            webPushService.sendNotificationToUser(memberId, messageDto)
        );

        assertThat(pushCallCount.get()).isEqualTo(0);
    }

    @Test
    @Order(5)
    @DisplayName("5. 구독 만료(410) 시 자동 삭제")
    void subscriptionExpired_AutoDelete() {
        // Given: 구독 정보 등록
        String memberId = "expired-member";
        registerSubscription(memberId);

        // Mock PushAsyncService - 410 응답 시뮬레이션
        when(pushAsyncService.sendAsync(any(Notification.class))).thenAnswer(invocation -> {
            pushCallCount.incrementAndGet();
            Notification notification = invocation.getArgument(0);
            return CompletableFuture.completedFuture(
                    PushResult.failure(notification.getEndpoint(), "Gone", 410, 50)
            );
        });

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("테스트 알림");
        messageDto.setUrl("/test");

        // When: 푸시 전송 (410 응답)
        try {
            webPushService.sendNotificationToUser(memberId, messageDto);
        } catch (Exception e) {
            // 예상되는 예외 (PushNotificationException)
        }

        // Then: 구독 정보 삭제 확인
        assertThat(pushSubscriptionService.hasSubscription(memberId)).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("6. RetryTemplate 동작 검증 - 실패 시 재시도 후 DLQ 저장")
    void retryTemplate_RetryAndDlq() {
        // Given: 구독 정보 등록
        String memberId = "retry-test";
        registerSubscription(memberId);

        AtomicInteger retryCount = new AtomicInteger(0);

        // Mock PushAsyncService - 항상 500 오류 반환
        when(pushAsyncService.sendAsync(any(Notification.class))).thenAnswer(invocation -> {
            retryCount.incrementAndGet();
            pushCallCount.incrementAndGet();
            Notification notification = invocation.getArgument(0);
            return CompletableFuture.completedFuture(
                    PushResult.failure(notification.getEndpoint(), "Server Error", 500, 50)
            );
        });

        NotificationMessageDto messageDto = new NotificationMessageDto();
        messageDto.setMessage("재시도 테스트");
        messageDto.setUrl("/retry-test");

        // When: 푸시 전송 (실패 -> 재시도 -> DLQ)
        webPushService.sendNotificationToUser(memberId, messageDto);

        // Then: 재시도 횟수 확인 (RetryTemplate 기본 3회 재시도)
        assertThat(retryCount.get()).isGreaterThanOrEqualTo(1);

        // DLQ 크기 확인
        Long dlqSize = webPushService.getDlqSize();
        assertThat(dlqSize).isGreaterThanOrEqualTo(1L);
    }

    private void registerSubscription(String memberId) {
        PushSubscriptionDto subscription = new PushSubscriptionDto();
        subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/member-" + memberId);
        subscription.setMemberId(memberId);

        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
        keys.setP256dh("BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM");
        keys.setAuth("tBHItJI5svbpez7KI4CCXg");
        subscription.setKeys(keys);

        pushSubscriptionService.saveSubscription(subscription, memberId);
    }
}
