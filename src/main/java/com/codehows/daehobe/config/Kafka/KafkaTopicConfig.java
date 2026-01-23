/**
 * @file KafkaTopicConfig.java
 * @description Kafka 토픽을 생성하고 관리하는 설정 클래스입니다.
 *              애플리케이션 시작 시 Spring에 의해 이 설정이 로드되며,
 *              정의된 토픽이 Kafka 브로커에 없는 경우 자동으로 생성됩니다.
 */

package com.codehows.daehobe.config.Kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;

/**
 * @class KafkaTopicConfig
 * @description Kafka 토픽을 생성하고 관리하는 Spring 설정 클래스입니다.
 *              애플리케이션 시작 시 정의된 토픽이 Kafka 브로커에 없는 경우 자동으로 생성됩니다.
 */
@Configuration // Spring 설정 클래스임을 나타냅니다.
public class KafkaTopicConfig {

    /**
     * @method notificationTopic
     * @description 푸시 알림 요청을 처리하기 위한 "notification-topic" 토픽을 생성하는 Bean입니다.
     *              `TopicBuilder`를 사용하여 토픽의 이름, 파티션 수, 복제본 수를 설정합니다.
     * @returns {NewTopic} Spring이 Kafka에 토픽을 등록하도록 하는 NewTopic 객체
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public NewTopic notificationTopic() {
        return TopicBuilder.name(NOTIFICATION_TOPIC)
                .partitions(2)
                .replicas(2)
                // .compact(): 이 옵션을 사용하면 로그 압축(Log Compaction)을 활성화할 수 있습니다.
                // 로그 압축은 토픽의 각 메시지 키에 대해 최소 하나 이상의 최신 값만 보존하는 전략입니다.
                // .config(TopicConfig.RETENTION_MS_CONFIG, "-1"): 메시지 보존 기간을 설정합니다.
                // "-1"은 무한 보존을 의미합니다.
                .build();
    }



    @Bean
    public NewTopic sttEncodingTopic() {
        return TopicBuilder.name(STT_ENCODING_TOPIC)
                .partitions(2)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic sttProcessingTopic() {
        return TopicBuilder.name(STT_PROCESSING_TOPIC)
                .partitions(2)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic sttSummarizingTopic() {
        return TopicBuilder.name(STT_SUMMARIZING_TOPIC)
                .partitions(2)
                .replicas(2)
                .build();
    }

}

    