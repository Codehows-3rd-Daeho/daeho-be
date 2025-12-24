/**
 * @file AppConfig.java
 * @description 애플리케이션 전반에 걸쳐 사용될 공통 빈(Bean)들을 정의하는 Spring 설정 클래스입니다.
 *              주로 서드파티 라이브러리 객체나 복잡한 초기화가 필요한 객체들을 빈으로 등록하여,
 *              다른 컴포넌트에서 의존성 주입(Dependency Injection)을 통해 사용할 수 있도록 합니다.
 */

package com.codehows.daehobe.config.webpush;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @class AppConfig
 * @description 애플리케이션 전반에 걸쳐 사용될 공통 빈들을 정의하는 Spring 설정 클래스입니다.
 *              주로 `ObjectMapper`와 같이 범용적으로 사용되는 유틸리티 객체들을 빈으로 등록합니다.
 */
@Configuration
public class AppConfig {

    /**
     * @method objectMapper
     * @description JSON 직렬화(Serialization) 및 역직렬화(Deserialization)를 위한 {@link ObjectMapper} 빈을 생성합니다.
     *              `ObjectMapper`는 Jackson 라이브러리의 핵심 클래스로, Java 객체와 JSON 데이터 간의 변환을 담당합니다.
     *              이 빈은 Java 8 날짜/시간 API(`java.time.*`)를 지원하도록 `JavaTimeModule`을 등록하고,
     *              날짜를 타임스탬프 대신 ISO 8601 문자열로 직렬화하도록 설정합니다.
     * @returns {ObjectMapper} 기본 설정으로 초기화된 ObjectMapper 인스턴스
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Java 8 날짜/시간 API 지원 모듈 등록
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 날짜를 타임스탬프 대신 ISO 8601 문자열로 직렬화
        return mapper;
    }
}
