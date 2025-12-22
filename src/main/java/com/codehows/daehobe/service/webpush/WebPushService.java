package com.codehows.daehobe.service.webpush;

import com.codehows.daehobe.dto.webpush.KafkaNotificationMessageDto;
import com.codehows.daehobe.dto.webpush.PushSubscriptionDto;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {
    /**
     * Redis에 푸시 구독 정보를 저장할 때 사용할 해시(Hash) 키입니다.
     * 이 키 아래에 사용자 ID를 필드로 하여 각 사용자의 구독 정보(JSON 문자열)를 저장합니다.
     */
    private static final String REDIS_SUBSCRIPTION_HASH_KEY = "web-push-subscriptions-by-user";

    /**
     * Redis 데이터베이스와 상호작용하기 위한 {@link RedisTemplate}입니다.
     * 주로 `opsForHash()`를 사용하여 해시 데이터 구조에 구독 정보를 저장하고 조회합니다.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 웹 푸시 메시지를 외부 푸시 서비스(예: Google FCM)로 전송하는 핵심 서비스입니다.
     * VAPID 키를 사용하여 메시지를 서명하고 암호화합니다.
     */
    private final PushService pushService;

    /**
     * Java 객체를 JSON 문자열로 변환하거나 그 반대로 변환하기 위한 {@link ObjectMapper}입니다.
     * 푸시 구독 DTO를 Redis에 저장하기 위해 JSON으로 직렬화하고, Redis에서 가져온 JSON을 다시 DTO로 역직렬화하는 데 사용됩니다.
     */
    private final ObjectMapper objectMapper;

    /**
     * @param {PushSubscriptionDto} subscriptionDto - 저장할 푸시 구독 정보
     * @param {String}              memberId - 구독을 요청한 사용자의 ID
     * @method saveSubscription
     * @description 클라이언트로부터 받은 푸시 구독 정보를 Redis에 저장합니다.
     * {@link PushSubscriptionDto} 객체를 JSON 문자열로 직렬화하여 Redis의 해시(Hash) 구조에 저장합니다.
     * 키는 `REDIS_SUBSCRIPTION_HASH_KEY`이고, 필드는 `subscriptionDto.getMemberId()`입니다.
     */
    public void saveSubscription(PushSubscriptionDto subscriptionDto, String memberId) {
        try {
            // 1. DTO 객체를 JSON 문자열로 변환
            // objectMapper는 Jackson 라이브러리를 사용하며, DTO 객체를 직렬화(Serialization).
            String subscriptionJson = objectMapper.writeValueAsString(subscriptionDto);

            // 2. Redis Hash에 저장
            // REDIS_SUBSCRIPTION_HASH_KEY : Redis에 구독 정보를 모아두는 Hash 키
            // memberId : Hash의 필드(field)로 사용 (각 사용자별 구독 정보를 구분)
            // subscriptionJson : Hash의 값(value)으로 JSON 형태의 구독 정보 저장
            redisTemplate.opsForHash().put(REDIS_SUBSCRIPTION_HASH_KEY, memberId, subscriptionJson);
            log.info("Subscription saved for member {}: {}", memberId, subscriptionJson);
            // 3. 저장 후 현재 Redis Hash 전체 내용 확인
            Map<Object, Object> allSubscriptions = redisTemplate.opsForHash().entries(REDIS_SUBSCRIPTION_HASH_KEY);
            if (allSubscriptions.isEmpty()) {
                log.info("현재 저장된 구독 정보가 없습니다.");
            } else {
                log.info("현재 Redis에 저장된 모든 구독 정보:");
                allSubscriptions.forEach((key, value) -> {
                    log.info("memberId: {}, subscription: {}", key, value);
                });
            }
        } catch (JsonProcessingException e) {
            log.error("Error saving subscription for member {}", memberId, e);
        }
    }

    /**
     * @param {String} memberId - 알림을 전송할 사용자의 ID
     * @param {String} message - 사용자에게 보낼 알림 메시지 내용
     * @method sendNotificationToUser
     * @description 특정 사용자에게 웹 푸시 알림을 전송합니다.
     * Redis에서 해당 사용자의 푸시 구독 정보를 조회하고, `PushService`를 사용하여 알림을 전송합니다.
     * 알림 전송 결과(HTTP 상태 코드)에 따라 만료된 구독을 Redis에서 제거하는 등의 후처리를 수행합니다.
     */
    public void sendNotificationToUser(String memberId, KafkaNotificationMessageDto messageDto) {
        // Redis에서 해당 사용자의 구독 정보를 조회합니다.
        String subscriptionJson = (String) redisTemplate.opsForHash().get(REDIS_SUBSCRIPTION_HASH_KEY, memberId);
        if (subscriptionJson == null) {
            log.info("No subscription found for member {}", memberId);
            return;
        }

        try {
            // JSON 문자열을 PushSubscriptionDto 객체로 역직렬화합니다.
            PushSubscriptionDto subDto = objectMapper.readValue(subscriptionJson, PushSubscriptionDto.class);
            // PushService 라이브러리에서 사용하는 Subscription 객체로 변환합니다.
            Subscription subscription = new Subscription(subDto.getEndpoint(), new Subscription.Keys(subDto.getKeys().getP256dh(), subDto.getKeys().getAuth()));

            // 푸시 알림 페이로드(JSON)를 생성합니다.
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("title", "새로운 알림"); // 알림 제목
            payload.put("body", messageDto.getMessage()); // 알림 내용
            payload.put("url", messageDto.getUrl()); // 알림 클릭 시 이동할 URL

            // Map을 JSON 문자열로 직렬화합니다.
            String payloadJson = objectMapper.writeValueAsString(payload);

            // Notification 객체를 생성. 직렬화된 JSON 문자열과 Urgency를 Notification 객체에 담아 전송합니다.
            Notification notification = new Notification(subscription, payloadJson, nl.martijndwars.webpush.Urgency.HIGH);

            // PushService를 통해 알림을 전송하고 응답을 받습니다.
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            // HTTP 상태 코드에 따른 후처리 로직
            if (statusCode == 410) { // GONE (410): 구독이 더 이상 유효하지 않음 (만료 또는 사용자 해지)
                log.info("Subscription for member {} expired or invalid. Removing.", memberId);
                redisTemplate.opsForHash().delete(REDIS_SUBSCRIPTION_HASH_KEY, memberId); // Redis에서 해당 구독 정보 삭제
            } else if (statusCode != 201) { // 201 CREATED: 성공적으로 알림이 전송되었음을 의미
                String responseBody = EntityUtils.toString(response.getEntity());
                log.warn("Failed to send push notification to member {}. Status: {}, Response: {}", memberId, statusCode, responseBody);
            }

        } catch (JsonProcessingException e) {
            log.error("Error deserializing subscription for member {}", memberId, e);
        } catch (GeneralSecurityException | IOException | JoseException | ExecutionException | InterruptedException e) {
            log.error("Error sending push notification for member {}", memberId, e);
        }
    }

}
