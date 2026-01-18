package com.codehows.daehobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Daeho-BE 애플리케이션의 메인 진입점입니다.
 * Spring Boot 애플리케이션을 초기화하고 실행합니다.
 *
 * @SpringBootApplication Spring Boot의 핵심 자동 설정, 컴포넌트 스캔 등을 활성화합니다.
 * @EnableKafka Spring for Apache Kafka 기능을 활성화하여 Kafka 리스너 및 템플릿을 사용할 수 있도록 합니다.
 * @EnableAsync Spring의 비동기 메서드 실행(@Async) 기능을 활성화합니다.
 * @EnableJpaAuditing JPA Auditing을 활성화하여 엔티티의 생성일, 수정일 등을 자동으로 관리합니다.
 *                     `auditorAwareRef`는 현재 사용자를 찾아주는 AuditorAware 구현체를 지정합니다.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
public class DaehoBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaehoBeApplication.class, args);
    }

}
