/**
 * @file KafkaProducerConfig.java
 * @description Kafka 메시지 생산(Producer) 관련 설정을 담당하는 Spring 설정 클래스입니다.
 *              이 클래스는 Kafka 브로커로 메시지를 전송하는 데 필요한 {@link ProducerFactory}와
 *              메시지 전송을 간편하게 해주는 {@link KafkaTemplate} 빈을 정의합니다.
 */

package com.codehows.daehobe.config.Kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @class KafkaProducerConfig
 * @description Kafka 메시지 생산자(Producer)를 설정하는 Spring 설정 클래스입니다.
 *              `ProducerFactory`와 `KafkaTemplate` 빈을 정의하여 Kafka 메시지 전송을 지원합니다.
 */
@Configuration // Spring 설정 클래스임을 나타냅니다.
public class KafkaProducerConfig {

    /**
     * Kafka 브로커의 주소 목록입니다.
     * `application.properties`에서 `spring.kafka.bootstrap-servers` 값으로 주입받습니다.
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * @method producerFactory
     * @description Kafka 프로듀서 인스턴스를 생성하기 위한 {@link ProducerFactory} 빈을 정의합니다.
     *              이 팩토리는 프로듀서가 Kafka 브로커에 연결하고 메시지를 전송하는 데 필요한 기본 속성들을 설정합니다.
     * @returns {ProducerFactory<String, String>} Kafka 프로듀서 생성을 위한 ProducerFactory 객체
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // BOOTSTRAP_SERVERS_CONFIG: 프로듀서가 처음 연결할 Kafka 브로커의 호스트와 포트 목록입니다.
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // KEY_SERIALIZER_CLASS_CONFIG: 메시지 키를 직렬화(Object -> byte[])하는 클래스입니다.
        // 컨슈머에서 사용하는 역직렬화 클래스(KEY_DESERIALIZER_CLASS_CONFIG)와 일치해야 합니다.
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // VALUE_SERIALIZER_CLASS_CONFIG: 메시지 값을 직렬화하는 클래스입니다.
        // 컨슈머에서 사용하는 역직렬화 클래스(VALUE_DESERIALIZER_CLASS_CONFIG)와 일치해야 합니다.
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // ACKS_CONFIG: 프로듀서가 메시지 전송 성공으로 간주하기 위한 확인(acknowledgement) 수준을 설정합니다.
        // "all" (또는 "-1"): 리더와 모든 팔로워 복제본에 메시지가 기록될 때까지 기다립니다. 가장 높은 내구성을 보장합니다.
        // "1": 리더 복제본에만 메시지가 기록되면 성공으로 간주합니다. (기본값)
        // "0": 확인을 기다리지 않고 즉시 메시지를 전송합니다. 가장 빠르지만 메시지 유실 가능성이 있습니다.
        // configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * @method kafkaTemplate
     * @description Kafka 메시지 전송을 위한 {@link KafkaTemplate} 빈을 생성합니다.
     *              `KafkaTemplate`은 Kafka 프로듀서 API를 고수준으로 추상화하여,
     *              개발자가 메시지를 편리하게 전송할 수 있도록 돕는 템플릿 클래스입니다.
     * @returns {KafkaTemplate<String, String>} Kafka 메시지 전송을 위한 KafkaTemplate 객체
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
