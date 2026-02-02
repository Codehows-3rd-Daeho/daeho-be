package com.codehows.daehobe.integration;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import nl.martijndwars.webpush.PushService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.mockito.Mockito.mock;

/**
 * 통합 테스트용 설정 - 외부 서비스(Redis, WebPush 등)를 Mock 처리
 */
@TestConfiguration
public class IntegrationTestConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        return mock(RedisTemplate.class);
    }

    @Bean
    @Primary
    public StringRedisTemplate hashRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public ChannelTopic websocketTopic() {
        return new ChannelTopic("test-channel");
    }

    @Bean
    @Primary
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        return mock(RedisMessageListenerContainer.class);
    }

    @Bean
    @Primary
    public PushService pushService() {
        return mock(PushService.class);
    }

    @Bean
    public org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer hibernate6Customizer() {
        return builder -> builder.modulesToInstall(new Hibernate6Module());
    }
}
