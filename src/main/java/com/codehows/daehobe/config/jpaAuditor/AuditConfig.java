package com.codehows.daehobe.config.jpaAuditor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

@Configuration //Jpa의 Auditing 기능 활성화
@RequiredArgsConstructor
public class AuditConfig {

    //auditorAware을 스프링 빈으로 등록해서 JPA가 쓸 수 있도록 함.
    @Bean
    public AuditorAware<Long> auditorProvider(){
        return new AuditorAwareImpl();
    }
}