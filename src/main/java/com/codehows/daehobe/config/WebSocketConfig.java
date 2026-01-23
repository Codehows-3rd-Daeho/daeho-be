package com.codehows.daehobe.config;

import com.codehows.daehobe.config.jwtAuth.StompHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket 설정
 * Redis Pub/Sub을 메시지 브로커로 사용하는 순수한 구조
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;

    /**
     * STOMP 엔드포인트 등록
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 메시지 브로커 설정
     * SimpleBroker 사용하지 않음 - Redis가 브로커 역할을 대체
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // SimpleBroker는 사용하지 않음
        // Redis Pub/Sub이 브로커 역할을 함

        // 클라이언트에서 서버로 메시지 전송 시 사용할 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 클라이언트 인바운드 채널 설정
     * 클라이언트 → 서버 메시지 인증/인가
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompHandler);
    }

    /**
     * WebSocket 전송 설정
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(10 * 1024 * 1024); // 10MB
        registration.setSendBufferSizeLimit(10 * 1024 * 1024); // 10MB
        registration.setSendTimeLimit(20000); // 20초
    }
}