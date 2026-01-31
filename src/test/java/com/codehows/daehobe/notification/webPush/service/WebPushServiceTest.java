package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.PushNotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class WebPushServiceTest {

    @Mock
    private PushSubscriptionService subscriptionService;
    @Mock
    private NotificationDlqService dlqService;
    @Mock
    private WebPushSender webPushSender;
    @Mock
    private RetryTemplate retryTemplate;

    private WebPushService webPushService;

    @BeforeEach
    void setUp() {
        webPushService = new WebPushService(subscriptionService, dlqService, webPushSender, retryTemplate);
    }

    @Test
    @DisplayName("성공: 푸시 알림 전송")
    void sendNotificationToUser_Success() throws Throwable {
        // given
        String memberId = "1";
        NotificationMessageDto messageDto = new NotificationMessageDto("테스트 메시지", "/url");
        PushSubscriptionDto subscription = createValidSubscription();

        when(subscriptionService.getSubscription(memberId)).thenReturn(Optional.of(subscription));
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            // RetryCallback 실행
            var callback = invocation.getArgument(0, org.springframework.retry.RetryCallback.class);
            return callback.doWithRetry(null);
        });

        // when
        webPushService.sendNotificationToUser(memberId, messageDto);

        // then
        verify(webPushSender).sendPushNotification(eq(memberId), eq(messageDto));
        verify(dlqService, never()).saveToDeadLetterQueue(anyString(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("무시: 구독 없음 - 전송 안함")
    void sendNotificationToUser_NoSubscription() {
        // given
        String memberId = "1";
        NotificationMessageDto messageDto = new NotificationMessageDto("테스트 메시지", "/url");

        when(subscriptionService.getSubscription(memberId)).thenReturn(Optional.empty());

        // when
        webPushService.sendNotificationToUser(memberId, messageDto);

        // then
        verify(webPushSender, never()).sendPushNotification(anyString(), any());
        verify(dlqService, never()).saveToDeadLetterQueue(anyString(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("실패: 모든 재시도 실패 시 DLQ 저장")
    void sendNotificationToUser_AllRetryFailed_SaveToDlq() throws Throwable {
        // given
        String memberId = "1";
        NotificationMessageDto messageDto = new NotificationMessageDto("테스트 메시지", "/url");
        PushSubscriptionDto subscription = createValidSubscription();

        when(subscriptionService.getSubscription(memberId)).thenReturn(Optional.of(subscription));
        when(retryTemplate.execute(any())).thenThrow(new PushNotificationException("Push failed", 500));

        // when
        webPushService.sendNotificationToUser(memberId, messageDto);

        // then
        verify(dlqService).saveToDeadLetterQueue(eq(memberId), eq(messageDto), eq(500), anyString());
    }

    @Test
    @DisplayName("실패: PushNotificationException이 아닌 예외 시 statusCode 0으로 DLQ 저장")
    void sendNotificationToUser_NonPushException_SaveToDlqWithZeroStatus() throws Throwable {
        // given
        String memberId = "1";
        NotificationMessageDto messageDto = new NotificationMessageDto("테스트 메시지", "/url");
        PushSubscriptionDto subscription = createValidSubscription();

        when(subscriptionService.getSubscription(memberId)).thenReturn(Optional.of(subscription));
        when(retryTemplate.execute(any())).thenThrow(new RuntimeException("Unknown error"));

        // when
        webPushService.sendNotificationToUser(memberId, messageDto);

        // then
        verify(dlqService).saveToDeadLetterQueue(eq(memberId), eq(messageDto), eq(0), anyString());
    }

    @Test
    @DisplayName("성공: 구독 저장 위임")
    void saveSubscription_Delegation() {
        // given
        PushSubscriptionDto subscription = createValidSubscription();
        String memberId = "1";

        // when
        webPushService.saveSubscription(subscription, memberId);

        // then
        verify(subscriptionService).saveSubscription(subscription, memberId);
    }

    @Test
    @DisplayName("성공: DLQ 크기 조회")
    void getDlqSize_Success() {
        // given
        when(dlqService.getDlqSize()).thenReturn(10L);

        // when
        Long result = webPushService.getDlqSize();

        // then
        verify(dlqService).getDlqSize();
    }

    private PushSubscriptionDto createValidSubscription() {
        PushSubscriptionDto dto = new PushSubscriptionDto();
        dto.setEndpoint("https://push.example.com/send/abc123");
        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
        keys.setP256dh("BLcmqL3J...");
        keys.setAuth("auth123");
        dto.setKeys(keys);
        return dto;
    }
}
