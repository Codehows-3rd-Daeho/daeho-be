/**
 * @file RedisConfig.java
 * @description Redis 서버와의 연결, 데이터 처리를 위한 {@link RedisTemplate},
 *              그리고 Pub/Sub 메시지 수신을 위한 {@link RedisMessageListenerContainer} 빈을 정의하는
 *              Spring 설정 클래스입니다.
 */

package com.codehows.daehobe.config.webpush;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @class RedisConfig
 * @description Redis 서버 연결 및 데이터 직렬화/역직렬화 설정을 담당하는 Spring 설정 클래스입니다.
 *              `RedisConnectionFactory`, `RedisTemplate`, `RedisMessageListenerContainer` 빈을 정의합니다.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost; // Redis 서버 호스트명

    @Value("${spring.data.redis.port}")
    private int redisPort; // Redis 서버 포트 번호

    /**
     * @method redisConnectionFactory
     * @description Redis 서버와의 연결을 생성하는 {@link RedisConnectionFactory} 빈을 정의합니다.
     *              고성능 비동기 클라이언트인 Lettuce를 사용하여 Redis 연결을 설정합니다.
     * @returns {RedisConnectionFactory} Lettuce 기반의 RedisConnectionFactory 인스턴스
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * @method redisTemplate
     * @description Redis 데이터 작업을 위한 고수준 추상화 클래스인 {@link RedisTemplate} 빈을 정의합니다.
     *              키는 문자열, 값은 객체로 직렬화/역직렬화되도록 설정합니다.
     * @returns {RedisTemplate<String, Object>} 직렬화 설정이 완료된 RedisTemplate 인스턴스
     */
    @Bean
    @Primary // 여러 RedisTemplate 빈이 있을 경우 이 빈을 우선적으로 사용하도록 지정합니다.
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        // Key Serializer: Redis 키를 문자열로 직렬화합니다.
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        // Value Serializer: Redis 값을 JSON 형식으로 직렬화합니다.
        // GenericJackson2JsonRedisSerializer는 Java 객체를 JSON으로 변환하여 저장하므로,
        // 다양한 타입의 객체를 유연하게 저장하고 역직렬화할 수 있습니다.
        // ObjectMapper를 커스터마이징하여 다형성 직렬화를 활성화합니다.
        ObjectMapper objectMapper = new ObjectMapper();
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class) // 모든 Object 타입에 대해 다형성 허용
                .build();
        // DefaultTyping.NON_FINAL을 사용하여 final이 아닌 모든 클래스에 대해 타입 정보를 JSON에 포함합니다.
        // JsonTypeInfo.As.PROPERTY는 타입 정보를 JSON 객체의 속성으로 추가합니다.
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        // Hash Key Serializer: 해시 데이터 구조의 필드(키)를 문자열로 직렬화합니다.
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        // Hash Value Serializer: 해시 데이터 구조의 값(value)을 JSON 형식으로 직렬화합니다.
        // Value Serializer와 동일한 ObjectMapper를 사용하여 일관성을 유지합니다.
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        return redisTemplate;
    }

    /**
     * @method redisMessageListenerContainer
     * @description Redis Pub/Sub 메시지를 수신하는 리스너 컨테이너 빈을 정의합니다.
     *              이 컨테이너는 특정 토픽(채널)에 대한 메시지 리스너를 등록하고, 메시지 수신을 관리합니다.
     * @param {RedisConnectionFactory} connectionFactory - Redis 연결을 위한 팩토리
     * @returns {RedisMessageListenerContainer} 설정이 완료된 RedisMessageListenerContainer 인스턴스
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        return container;
    }
}