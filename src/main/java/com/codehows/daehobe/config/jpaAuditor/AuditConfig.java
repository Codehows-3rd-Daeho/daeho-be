package com.codehows.daehobe.config.jpaAuditor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration //Jpa의 Auditing 기능 활성화
@RequiredArgsConstructor
@EnableJpaAuditing(auditorAwareRef = "auditorProvider") // "auditorProvider"는 AuditConfig에 정의한 Bean 이름
public class AuditConfig {

    //auditorAware을 스프링 빈으로 등록해서 JPA가 쓸 수 있도록 함.
    @Bean
    public AuditorAware<Long> auditorProvider(){
        return new AuditorAwareImpl();
    }
}