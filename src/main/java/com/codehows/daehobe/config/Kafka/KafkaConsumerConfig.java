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
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;

/**
 * @class KafkaConsumerConfig
 * @description Kafka 메시지 컨슈머(Consumer)를 설정하는 Spring 설정 클래스입니다.
 *              `ConsumerFactory`와 `ConcurrentKafkaListenerContainerFactory` 빈을 정의하여
 *              Kafka 메시지 소비를 지원합니다.
 */
@Configuration // Spring 설정 클래스임을 나타냅니다.
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    /**
     * @method consumerFactory
     * @description Kafka 컨슈머 인스턴스를 생성하기 위한 {@link ConsumerFactory} 빈을 정의합니다.
     *              이 팩토리는 컨슈머가 Kafka 브로커에 연결하고 메시지를 수신하는 데 필요한 기본 속성들을 설정합니다.
     * @returns {ConsumerFactory<String, String>} Kafka 컨슈머 생성을 위한 ConsumerFactory 객체
     */
    @Bean
    public ConsumerFactory<String, String> notificationGroupConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(NOTIFICATION_GROUP));
    }

    /**
     * @method kafkaListenerContainerFactory
     * @description {@code @KafkaListener} 어노테이션이 붙은 메서드를 위한 리스너 컨테이너 팩토리를 생성하는 빈입니다.
     *              이 팩토리는 동시성(concurrency) 제어 등 리스너의 동작 방식을 설정합니다.
     * @returns {ConcurrentKafkaListenerContainerFactory<String, String>} ConcurrentKafkaListenerContainerFactory 객체
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> notificationListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationGroupConsumerFactory());
        factory.setConcurrency(2);
        return factory;
    }



    @Bean
    public ConsumerFactory<String, String> sttEncodingGroupConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(STT_ENCODING_GROUP));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> sttEncodingListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sttEncodingGroupConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> sttProcessingGroupConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(STT_PROCESSING_GROUP));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> sttProcessingListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sttProcessingGroupConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> sttSummarizingGroupConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps(STT_SUMMARIZING_GROUP));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> sttSummarizingListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sttSummarizingGroupConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
