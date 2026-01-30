package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.PushNotificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 웹 푸시 서비스 (Facade)
 * 책임: 구독 관리 위임, 재시도 로직 조율, DLQ 연계
 */
@Slf4j
@Service
public class WebPushService {

    private final PushSubscriptionService subscriptionService;
    private final NotificationDlqService dlqService;
    private final WebPushSender webPushSender;
    private final RetryTemplate retryTemplate;

    public WebPushService(
            PushSubscriptionService subscriptionService,
            NotificationDlqService dlqService,
            WebPushSender webPushSender,
            RetryTemplate webPushRetryTemplate
    ) {
        this.subscriptionService = subscriptionService;
        this.dlqService = dlqService;
        this.webPushSender = webPushSender;
        this.retryTemplate = webPushRetryTemplate;
    }

    // 구독 관리 위임 메서드 (Controller 호환성 유지)
    public void saveSubscription(PushSubscriptionDto subscriptionDto, String memberId) {
        subscriptionService.saveSubscription(subscriptionDto, memberId);
    }

    /**
     * 특정 회원에게 푸시 알림 전송 (Jitter 적용된 재시도 포함)
     */
    public void sendNotificationToUser(String memberId, NotificationMessageDto messageDto) {
        Optional<PushSubscriptionDto> subscriptionOpt = subscriptionService.getSubscription(memberId);

        if (subscriptionOpt.isEmpty()) {
            log.info("No subscription found for member {}", memberId);
            return;
        }

        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("Retry attempt {} for member {}", context.getRetryCount(), memberId);
                }
                webPushSender.sendPushNotification(memberId, messageDto);
                return null;
            });
        } catch (Exception e) {
            log.error("All retry attempts failed for member {}. Moving to DLQ.", memberId, e);
            int statusCode = (e instanceof PushNotificationException)
                    ? ((PushNotificationException) e).getStatusCode()
                    : 0;
            dlqService.saveToDeadLetterQueue(memberId, messageDto, statusCode, e.getMessage());
        }
    }

    // DLQ 크기 조회 (모니터링용)
    public Long getDlqSize() {
        return dlqService.getDlqSize();
    }
}
