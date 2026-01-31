package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.PushResult;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.PushNotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 웹 푸시 전송 구현체 (동기 전송)
 * DLQ 재처리에서 사용하여 순환 의존성 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender {

    private final PushSubscriptionService subscriptionService;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public void sendPushNotification(String memberId, NotificationMessageDto messageDto) {
        Optional<PushSubscriptionDto> subscriptionOpt = subscriptionService.getSubscription(memberId);

        if (subscriptionOpt.isEmpty()) {
            log.info("No subscription found for member {} during push attempt", memberId);
            throw new PushNotificationException("No subscription found for member: " + memberId);
        }

        doSendPushNotification(memberId, messageDto, subscriptionOpt.get());
    }

    private void doSendPushNotification(String memberId, NotificationMessageDto messageDto,
                                         PushSubscriptionDto subDto) {
        try {
            Subscription subscription = new Subscription(
                    subDto.getEndpoint(),
                    new Subscription.Keys(
                            subDto.getKeys().getP256dh(),
                            subDto.getKeys().getAuth()
                    )
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("title", "새로운 알림");
            payload.put("body", messageDto.getMessage());
            payload.put("url", messageDto.getUrl());
            String payloadJson = objectMapper.writeValueAsString(payload);

            Notification notification = new Notification(
                    subscription,
                    payloadJson,
                    nl.martijndwars.webpush.Urgency.HIGH
            );

            // PushService 직접 호출 (동기)
            PushResult result = sendPush(notification);
            handleResult(memberId, subDto.getEndpoint(), result);

            if (!result.isSuccess()) {
                throw new PushNotificationException(
                        "Push failed: " + result.getErrorMessage(),
                        result.getStatusCode()
                );
            }

        } catch (JsonProcessingException e) {
            log.error("Error serializing payload for member {}", memberId, e);
            throw new PushNotificationException("Payload serialization failed", e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            log.error("Error creating notification for member {}", memberId, e);
            throw new PushNotificationException("Notification creation failed", e);
        }
    }

    private PushResult sendPush(Notification notification) {
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            long latency = System.currentTimeMillis() - startTime;

            if (statusCode >= 200 && statusCode < 300) {
                return PushResult.success(notification.getEndpoint(), statusCode, latency);
            } else {
                return PushResult.failure(notification.getEndpoint(), "HTTP " + statusCode, statusCode, latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Error sending push notification to {}: {}", notification.getEndpoint(), e.getMessage());
            return PushResult.failure(notification.getEndpoint(), e.getMessage(), latency);
        }
    }

    private void handleResult(String memberId, String endpoint, PushResult result) {
        if (result.isSuccess()) {
            log.info("Push sent successfully to member {} (latency: {}ms)", memberId, result.getLatencyMs());
        } else {
            if (result.getStatusCode() == 410) {
                log.warn("Subscription expired for member {}. Removing from Redis.", memberId);
                subscriptionService.deleteSubscription(memberId);
            } else {
                log.warn("Push failed for member {}: {} (statusCode: {})",
                        memberId, result.getErrorMessage(), result.getStatusCode());
            }
        }
    }
}
