package com.codehows.daehobe.config.redis;

import com.codehows.daehobe.common.dto.RedisWebSocketMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 기반 메시지 브로커
 * 컨트롤러에서 메시지를 발행할 때 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageBroker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic websocketTopic;

    /**
     * 메시지를 Redis Pub/Sub으로 발행
     * 모든 서버 인스턴스가 구독하고 있으며, 각자 연결된 클라이언트에게 전달
     *
     * @param destination WebSocket destination (예: /topic/chat/1)
     * @param payload 전송할 메시지 객체
     */
    public void publish(String destination, Object payload) {
        try {
            RedisWebSocketMessage message = new RedisWebSocketMessage(destination, payload);
            String jsonMessage = objectMapper.writeValueAsString(message);

            redisTemplate.convertAndSend(websocketTopic.getTopic(), jsonMessage);

            log.debug("Published to Redis - Destination: {}, Payload: {}", destination, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message - Destination: {}, Payload: {}",
                    destination, payload, e);
            throw new RuntimeException("Message serialization failed", e);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis - Destination: {}, Payload: {}",
                    destination, payload, e);
            throw new RuntimeException("Message publish failed", e);
        }
    }
}