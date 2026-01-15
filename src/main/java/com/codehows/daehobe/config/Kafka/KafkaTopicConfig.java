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
        return TopicBuilder.name("notification-topic") // 토픽의 이름을 "notification-topic"으로 설정합니다.
                // partitions(1): 토픽의 파티션 수를 1로 설정합니다.
                // 파티션은 토픽을 구성하는 데이터의 하위 집합으로, 병렬 처리를 가능하게 합니다.
                // 컨슈머 그룹 내의 컨슈머 수만큼 파티션을 늘리면 처리량을 높일 수 있습니다.
                .partitions(10)

                // replicas(1): 각 파티션의 복제본 수를 1로 설정합니다.
                // 복제본은 데이터의 고가용성(High Availability)과 내구성(Durability)을 보장합니다.
                // 운영 환경에서는 보통 2 또는 3 이상의 값을 권장하지만, 로컬 개발 환경이므로 1로 설정합니다.
                .replicas(2)

                // .compact(): 이 옵션을 사용하면 로그 압축(Log Compaction)을 활성화할 수 있습니다.
                // 로그 압축은 토픽의 각 메시지 키에 대해 최소 하나 이상의 최신 값만 보존하는 전략입니다.
                // .config(TopicConfig.RETENTION_MS_CONFIG, "-1"): 메시지 보존 기간을 설정합니다.
                // "-1"은 무한 보존을 의미합니다.
                .build();
    }

}

    