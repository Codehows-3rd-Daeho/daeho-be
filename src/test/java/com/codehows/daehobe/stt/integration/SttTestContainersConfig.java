package com.codehows.daehobe.stt.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;

/**
 * STT 통합 테스트를 위한 Testcontainers 설정
 * - Redis 컨테이너 (redis:7-alpine)
 * - Kafka 컨테이너 (confluentinc/cp-kafka:7.6.0, KRaft 모드)
 * - WireMock 서버 (Daglo API Mock)
 */
@TestConfiguration
@Testcontainers
public class SttTestContainersConfig {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    ).withKraft();

    private static WireMockServer wireMockServer;

    static {
        redisContainer.start();
        kafkaContainer.start();

        // Kafka 토픽 생성
        createKafkaTopics();

        // WireMock 서버 시작
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort());
        wireMockServer.start();
    }

    private static void createKafkaTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            List<NewTopic> topics = List.of(
                    new NewTopic(STT_ENCODING_TOPIC, 1, (short) 1),
                    new NewTopic(STT_PROCESSING_TOPIC, 1, (short) 1),
                    new NewTopic(STT_SUMMARIZING_TOPIC, 1, (short) 1),
                    new NewTopic(NOTIFICATION_TOPIC, 1, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis 설정
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Kafka 설정
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");

        // WireMock (Daglo API) 설정
        registry.add("daglo.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Bean
    public WireMockServer wireMockServer() {
        return wireMockServer;
    }

    public static RedisContainer getRedisContainer() {
        return redisContainer;
    }

    public static KafkaContainer getKafkaContainer() {
        return kafkaContainer;
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

    public static String getKafkaBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    public static int getWireMockPort() {
        return wireMockServer.port();
    }
}
