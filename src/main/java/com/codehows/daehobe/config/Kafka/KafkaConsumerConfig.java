/**
 * @file KafkaConsumerConfig.java
 * @description Kafka 메시지 소비(Consumer) 관련 설정을 담당하는 Spring 설정 클래스입니다.
 *              이 클래스는 Kafka 브로커로부터 메시지를 수신하는 데 필요한 {@link ConsumerFactory}와
 *              {@code @KafkaListener} 어노테이션을 처리하는 {@link ConcurrentKafkaListenerContainerFactory} 빈을 정의합니다.
 */

package com.codehows.daehobe.config.Kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @class KafkaConsumerConfig
 * @description Kafka 메시지 컨슈머(Consumer)를 설정하는 Spring 설정 클래스입니다.
 *              `ConsumerFactory`와 `ConcurrentKafkaListenerContainerFactory` 빈을 정의하여
 *              Kafka 메시지 소비를 지원합니다.
 */
@Configuration // Spring 설정 클래스임을 나타냅니다.
public class KafkaConsumerConfig {

    /**
     * Kafka 브로커의 주소 목록입니다.
     * `application.properties`에서 `spring.kafka.bootstrap-servers` 값으로 주입받습니다.
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Kafka 컨슈머 그룹 ID입니다.
     * `application.properties`에서 `spring.kafka.consumer.group-id` 값으로 주입받습니다.
     */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * @method consumerFactory
     * @description Kafka 컨슈머 인스턴스를 생성하기 위한 {@link ConsumerFactory} 빈을 정의합니다.
     *              이 팩토리는 컨슈머가 Kafka 브로커에 연결하고 메시지를 수신하는 데 필요한 기본 속성들을 설정합니다.
     * @returns {ConsumerFactory<String, String>} Kafka 컨슈머 생성을 위한 ConsumerFactory 객체
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // BOOTSTRAP_SERVERS_CONFIG: 컨슈머가 처음 연결할 Kafka 브로커의 호스트와 포트 목록입니다.
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // GROUP_ID_CONFIG: 컨슈머가 속한 그룹의 ID입니다.
        // 동일한 그룹 ID를 가진 컨슈머들은 하나의 토픽의 다른 파티션들을 나누어 처리함으로써 메시지 처리의 병렬성을 높입니다.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // KEY_DESERIALIZER_CLASS_CONFIG: 메시지 키를 역직렬화(byte[] -> Object)하는 클래스입니다.
        // 프로듀서에서 사용한 직렬화 클래스(KEY_SERIALIZER_CLASS_CONFIG)와 일치해야 합니다.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // VALUE_DESERIALIZER_CLASS_CONFIG: 메시지 값을 역직렬화하는 클래스입니다.
        // 프로듀서에서 사용한 직렬화 클래스(VALUE_SERIALIZER_CLASS_CONFIG)와 일치해야 합니다.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // AUTO_OFFSET_RESET_CONFIG: 컨슈머가 처음 시작하거나 기존 오프셋을 찾을 수 없을 때 어디서부터 메시지를 읽을지 결정합니다.
        // "earliest": 가장 오래된 메시지부터 읽기 시작합니다.
        // "latest": 가장 최근(새로운) 메시지부터 읽기 시작합니다. (기본값)
        // "none": 오프셋을 찾지 못하면 예외를 발생시킵니다.
        // 이 설정은 보통 application.properties (`spring.kafka.consumer.auto-offset-reset`)에서 관리하는 것이 일반적입니다.
        // props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * @method kafkaListenerContainerFactory
     * @description {@code @KafkaListener} 어노테이션이 붙은 메서드를 위한 리스너 컨테이너 팩토리를 생성하는 빈입니다.
     *              이 팩토리는 동시성(concurrency) 제어 등 리스너의 동작 방식을 설정합니다.
     * @returns {ConcurrentKafkaListenerContainerFactory<String, String>} ConcurrentKafkaListenerContainerFactory 객체
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        // 위에서 정의한 consumerFactory()를 사용하여 컨슈머 인스턴스를 생성하도록 설정합니다.
        factory.setConsumerFactory(consumerFactory());

        // factory.setConcurrency(3): 리스너의 동시성을 설정할 수 있습니다.
        // 예를 들어, 3으로 설정하면 3개의 스레드가 동시에 메시지를 처리합니다.
        // 이는 토픽의 파티션 수와 관련이 깊으며, 보통 파티션 수와 맞추거나 더 작게 설정합니다.

        return factory;
    }
}
