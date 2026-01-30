package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
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
import org.apache.http.util.EntityUtils;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * 웹 푸시 전송 구현체 (순수 전송만 담당)
 * DLQ 재처리에서 사용하여 순환 의존성 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSenderImpl implements WebPushSender {

    private final PushSubscriptionService subscriptionService;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Override
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

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            handlePushResponse(memberId, statusCode, response);

        } catch (JsonProcessingException e) {
            log.error("Error serializing payload for member {}", memberId, e);
            throw new PushNotificationException("Payload serialization failed", e);
        } catch (GeneralSecurityException | IOException | JoseException | ExecutionException | InterruptedException e) {
            log.error("Error sending push notification for member {}", memberId, e);
            throw new PushNotificationException("Push notification failed", e);
        }
    }

    private void handlePushResponse(String memberId, int statusCode, HttpResponse response) {
        if (statusCode == 201) {
            log.info("Push sent successfully to member {}", memberId);
        } else if (statusCode == 410) {
            log.warn("Subscription expired for member {}. Removing from Redis.", memberId);
            subscriptionService.deleteSubscription(memberId);
            throw new PushNotificationException("Subscription expired", 410);
        } else {
            String responseBody = "";
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (Exception ignored) {}

            log.error("Push failed. Status: {}, Response: {}", statusCode, responseBody);
            throw new PushNotificationException("Push failed with status: " + statusCode, statusCode);
        }
    }
}
