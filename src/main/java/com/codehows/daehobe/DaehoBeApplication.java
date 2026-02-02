package com.codehows.daehobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Daeho-BE 애플리케이션의 메인 진입점입니다.
 * Spring Boot 애플리케이션을 초기화하고 실행합니다.
 *
 * @SpringBootApplication Spring Boot의 핵심 자동 설정, 컴포넌트 스캔 등을 활성화합니다.
 * @EnableAsync 및 @EnableScheduling은 AsyncConfig, SchedulingConfig에서 활성화됩니다.
 */
@SpringBootApplication
public class DaehoBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaehoBeApplication.class, args);
    }

}
