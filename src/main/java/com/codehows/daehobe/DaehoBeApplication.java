package com.codehows.daehobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
//@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class DaehoBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaehoBeApplication.class, args);
    }

}
