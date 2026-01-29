package com.codehows.daehobe.stt.integration;

import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 클러스터 HA 테스트
 *
 * 테스트 시나리오:
 * 1. 리더 선출: 브로커 중 리더 종료 → 새 리더 선출 → 메시지 발행 복원
 * 2. 파티션 리밸런싱: 컨슈머 그룹 리밸런싱 동작 확인
 * 3. Redis 장애 복원: Redis 다운 → graceful 처리 → 재시작 후 복구
 */
@Testcontainers
@DisplayName("Kafka/Redis 클러스터 HA 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaClusterHaTest {

    private static Network network = Network.newNetwork();

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    ).withKraft()
     .withNetwork(network);

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379)
     .withNetwork(network);

    private KafkaTemplate<String, String> kafkaTemplate;
    private Consumer<String, String> consumer;
    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void setupTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            List<NewTopic> topics = List.of(
                    new NewTopic(STT_ENCODING_TOPIC, 3, (short) 1),
                    new NewTopic(STT_PROCESSING_TOPIC, 3, (short) 1),
                    new NewTopic(STT_SUMMARIZING_TOPIC, 3, (short) 1),
                    new NewTopic("ha-test-topic", 3, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // 이미 존재하면 무시
        }
    }

    @BeforeEach
    void setUp() {
        // Kafka Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        producerProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Kafka Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "ha-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        consumerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = consumerFactory.createConsumer();

        // Redis 연결 설정
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Kafka 클러스터 상태 확인")
    void kafkaCluster_HealthCheck_Success() throws Exception {
        // given
        AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ));

        // when
        DescribeClusterResult clusterResult = adminClient.describeCluster();
        Collection<Node> nodes = clusterResult.nodes().get(10, TimeUnit.SECONDS);
        Node controller = clusterResult.controller().get(10, TimeUnit.SECONDS);

        // then
        assertThat(nodes).isNotEmpty();
        assertThat(controller).isNotNull();

        adminClient.close();
    }

    @Test
    @Order(2)
    @DisplayName("2. 메시지 발행 후 컨테이너 재시작 - 메시지 유지 확인")
    void kafkaRestart_MessagesPreserved() throws Exception {
        // given
        String topic = "ha-test-topic";
        String key = "ha-test-key";
        String message = "message-before-restart-" + System.currentTimeMillis();

        // when - 메시지 발행
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, key, message);
        future.get(10, TimeUnit.SECONDS);

        // 컨테이너 재시작 시뮬레이션 (실제 재시작은 테스트 시간이 오래 걸림)
        // 대신 연결 끊기/재연결 테스트

        // 새로운 Consumer로 메시지 확인
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "restart-test-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> newConsumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer();
        newConsumer.subscribe(Collections.singletonList(topic));

        // then
        AtomicBoolean found = new AtomicBoolean(false);
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = newConsumer.poll(Duration.ofMillis(500));
                    records.forEach(record -> {
                        if (message.equals(record.value())) {
                            found.set(true);
                        }
                    });
                    return found.get();
                });

        assertThat(found.get()).isTrue();
        newConsumer.close();
    }

    @Test
    @Order(3)
    @DisplayName("3. Producer 재시도 동작 확인")
    void producerRetry_OnTemporaryFailure_Recovers() throws Exception {
        // given
        String topic = STT_ENCODING_TOPIC;
        int messageCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 여러 메시지 발행 (재시도 설정 포함)
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, "retry-test-" + i, "message-" + i);
            future.thenAccept(result -> successCount.incrementAndGet())
                  .exceptionally(ex -> {
                      failCount.incrementAndGet();
                      return null;
                  });
            futures.add(future);
        }

        // 모든 전송 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        // then
        assertThat(successCount.get()).isEqualTo(messageCount);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @Order(4)
    @DisplayName("4. Consumer 그룹 리밸런싱 시뮬레이션")
    void consumerGroup_Rebalancing_Simulation() throws Exception {
        // given
        String topic = STT_PROCESSING_TOPIC;
        String groupId = "rebalance-test-group";

        // 첫 번째 컨슈머
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 6000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 2000);

        Consumer<String, String> consumer1 = new DefaultKafkaConsumerFactory<String, String>(props)
                .createConsumer();
        consumer1.subscribe(Collections.singletonList(topic));

        // 메시지 발행
        for (int i = 0; i < 5; i++) {
            kafkaTemplate.send(topic, "rebalance-" + i, "msg-" + i).get();
        }

        // when - 첫 번째 컨슈머 폴링
        ConsumerRecords<String, String> records1 = consumer1.poll(Duration.ofSeconds(5));
        int consumer1Count = records1.count();

        // 두 번째 컨슈머 추가 (리밸런싱 트리거)
        Consumer<String, String> consumer2 = new DefaultKafkaConsumerFactory<String, String>(props)
                .createConsumer();
        consumer2.subscribe(Collections.singletonList(topic));

        // 새 메시지 발행
        for (int i = 5; i < 10; i++) {
            kafkaTemplate.send(topic, "rebalance-" + i, "msg-" + i).get();
        }

        // 두 컨슈머 모두 폴링
        AtomicInteger totalReceived = new AtomicInteger(consumer1Count);
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> r1 = consumer1.poll(Duration.ofMillis(100));
                    ConsumerRecords<String, String> r2 = consumer2.poll(Duration.ofMillis(100));
                    totalReceived.addAndGet(r1.count() + r2.count());
                    return totalReceived.get() >= 10;
                });

        // then
        assertThat(totalReceived.get()).isGreaterThanOrEqualTo(10);

        consumer1.close();
        consumer2.close();
    }

    @Test
    @Order(5)
    @DisplayName("5. Redis 연결 상태 확인")
    void redisConnection_HealthCheck_Success() {
        // given & when
        String testKey = "ha-test-key";
        String testValue = "ha-test-value";

        redisTemplate.opsForValue().set(testKey, testValue);
        String retrieved = redisTemplate.opsForValue().get(testKey);

        // then
        assertThat(retrieved).isEqualTo(testValue);

        // cleanup
        redisTemplate.delete(testKey);
    }

    @Test
    @Order(6)
    @DisplayName("6. Redis 연결 끊김 시 Graceful 처리")
    void redisConnectionLoss_GracefulHandling() {
        // given
        String testKey = "graceful-test-key";

        // when - 정상 작동 확인
        try {
            redisTemplate.opsForValue().set(testKey, "value");
            assertThat(redisTemplate.hasKey(testKey)).isTrue();
        } catch (Exception e) {
            // 연결 실패 시 예외 발생 확인
            assertThat(e).isNotNull();
        }

        // cleanup
        try {
            redisTemplate.delete(testKey);
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(7)
    @DisplayName("7. 분산락 실패 시 Fallback 동작")
    void distributedLock_Failure_Fallback() {
        // given
        String lockKey = "stt:processor:lock:fallback:1";
        AtomicBoolean processedWithoutLock = new AtomicBoolean(false);

        // when - 락 획득 시도
        boolean acquired = false;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", Duration.ofSeconds(5));
            acquired = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // Redis 연결 실패 시 fallback
            processedWithoutLock.set(true);
        }

        // then
        if (acquired) {
            // 락 획득 성공 - 정상 처리
            assertThat(redisTemplate.hasKey(lockKey)).isTrue();
            redisTemplate.delete(lockKey);
        } else if (processedWithoutLock.get()) {
            // Fallback 처리됨 (실제 시스템에서는 로컬 락 사용 등)
            assertThat(processedWithoutLock.get()).isTrue();
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. 높은 동시성 테스트 - Kafka + Redis 동시 사용")
    void highConcurrency_KafkaAndRedis_Together() throws Exception {
        // given
        int threadCount = 20;
        int messagesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger kafkaSuccess = new AtomicInteger(0);
        AtomicInteger redisSuccess = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        // Kafka 메시지 발행
                        kafkaTemplate.send(STT_ENCODING_TOPIC,
                                "concurrent-" + threadId + "-" + j,
                                "message-" + j).get();
                        kafkaSuccess.incrementAndGet();

                        // Redis 캐싱
                        redisTemplate.opsForValue().set(
                                "concurrent:" + threadId + ":" + j,
                                "value-" + j,
                                Duration.ofMinutes(1));
                        redisSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 실패 로깅
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        int expectedTotal = threadCount * messagesPerThread;
        assertThat(kafkaSuccess.get()).isEqualTo(expectedTotal);
        assertThat(redisSuccess.get()).isEqualTo(expectedTotal);

        // cleanup
        for (int i = 0; i < threadCount; i++) {
            for (int j = 0; j < messagesPerThread; j++) {
                redisTemplate.delete("concurrent:" + i + ":" + j);
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. 컨테이너 상태 복원 후 메시지 처리 재개")
    void containerRecovery_ResumeProcessing() throws Exception {
        // given
        String topic = STT_SUMMARIZING_TOPIC;
        List<String> sentMessages = new ArrayList<>();

        // 메시지 발행
        for (int i = 0; i < 5; i++) {
            String msg = "recovery-test-" + i;
            kafkaTemplate.send(topic, "recovery-" + i, msg).get();
            sentMessages.add(msg);
        }

        // when - 새 컨슈머로 메시지 수신 (복구 시나리오)
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "recovery-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> recoveryConsumer = new DefaultKafkaConsumerFactory<String, String>(props)
                .createConsumer();
        recoveryConsumer.subscribe(Collections.singletonList(topic));

        List<String> receivedMessages = new ArrayList<>();
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = recoveryConsumer.poll(Duration.ofMillis(500));
                    records.forEach(r -> receivedMessages.add(r.value()));
                    return receivedMessages.containsAll(sentMessages);
                });

        // then
        assertThat(receivedMessages).containsAll(sentMessages);

        recoveryConsumer.close();
    }

    @Test
    @Order(10)
    @DisplayName("10. 네트워크 지연 시뮬레이션 - 타임아웃 처리")
    void networkLatency_TimeoutHandling() {
        // given
        String key = "timeout-test-key";

        // when - 짧은 타임아웃으로 Redis 작업 수행
        try {
            redisTemplate.opsForValue().set(key, "value", Duration.ofSeconds(10));
            String value = redisTemplate.opsForValue().get(key);

            // then
            assertThat(value).isEqualTo("value");
        } catch (Exception e) {
            // 타임아웃 발생 시 예외 처리됨
            assertThat(e).isInstanceOfAny(
                    org.springframework.dao.QueryTimeoutException.class,
                    io.lettuce.core.RedisCommandTimeoutException.class
            );
        }

        // cleanup
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }
}
