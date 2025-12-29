package com.codehows.daehobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorProvider") // "auditorProvider"는 AuditConfig에 정의한 Bean 이름
public class DaehoBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaehoBeApplication.class, args);
    }

}
