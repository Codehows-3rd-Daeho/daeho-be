package com.codehows.daehobe.stt.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * STT 통합 테스트를 위한 Testcontainers 설정
 * - Redis 컨테이너 (redis:7-alpine)
 * - WireMock 서버 (Daglo API Mock)
 */
@TestConfiguration
@Testcontainers
public class SttTestContainersConfig {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static WireMockServer wireMockServer;

    static {
        redisContainer.start();

        // WireMock 서버 시작
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort());
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis 설정
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // WireMock (Daglo API) 설정
        registry.add("daglo.api.base-url", () -> "http://localhost:" + wireMockServer.port());

        // STT Polling 설정
        registry.add("stt.polling.interval-ms", () -> "500");
        registry.add("stt.polling.max-attempts", () -> "10");
    }

    @Bean
    public WireMockServer wireMockServer() {
        return wireMockServer;
    }

    public static RedisContainer getRedisContainer() {
        return redisContainer;
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    public static String getRedisHost() {
        return redisContainer.getHost();
    }

    public static int getRedisPort() {
        return redisContainer.getMappedPort(6379);
    }

    public static int getWireMockPort() {
        return wireMockServer.port();
    }
}
