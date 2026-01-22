package com.codehows.daehobe.config.redis;

import com.codehows.daehobe.common.dto.RedisWebSocketMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Redis 구독자 설정
 * SimpleBroker 없이 직접 WebSocket 세션에 메시지 전달
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisSubscriberConfig {

    private final ObjectMapper objectMapper;

    /**
     * Redis 메시지 리스너 컨테이너
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter,
            ChannelTopic websocketTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, websocketTopic);

        return container;
    }

    /**
     * Redis 메시지 리스너 어댑터
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * Redis 구독자 빈
     */
    @Bean
    public RedisMessageSubscriber redisMessageSubscriber(SimpMessagingTemplate messagingTemplate) {
        return new RedisMessageSubscriber(messagingTemplate, objectMapper);
    }

    /**
     * Redis 메시지 구독자
     * Redis에서 메시지를 받아 WebSocket으로 전달
     */
    @Slf4j
    @RequiredArgsConstructor
    public static class RedisMessageSubscriber implements MessageListener {

        private final SimpMessagingTemplate messagingTemplate;
        private final ObjectMapper objectMapper;

        /**
         * Redis Pub/Sub 메시지 수신 처리
         *
         * @param message Redis에서 수신한 JSON 메시지
         */
        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                // byte[]를 String으로 변환
                String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);

                log.debug("Received Redis message: {}", messageBody);

                // RedisWebSocketMessage 객체로 파싱
                RedisWebSocketMessage redisMessage = objectMapper.readValue(
                        messageBody,
                        RedisWebSocketMessage.class
                );

                String destination = redisMessage.getDestination();
                Object payload = redisMessage.getPayload();

                // payload가 LinkedHashMap으로 역직렬화되었을 경우 재변환
                if (payload instanceof java.util.LinkedHashMap) {
                    log.debug("Payload is LinkedHashMap, converting to JSON string for WebSocket");
                    messagingTemplate.convertAndSend(destination, payload);
                } else {
                    messagingTemplate.convertAndSend(destination, payload);
                }

                log.debug("Message sent to WebSocket - Destination: {}", destination);
            } catch (Exception e) {
                log.error("Failed to process Redis message", e);
            }
        }
    }
}