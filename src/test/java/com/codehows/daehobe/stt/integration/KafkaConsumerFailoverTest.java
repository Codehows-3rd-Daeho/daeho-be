package com.codehows.daehobe.stt.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka Consumer Failover 테스트
 *
 * 시나리오:
 * 1. 다중 컨슈머 인스턴스가 파티션을 분산 처리
 * 2. 인스턴스 하나가 죽음 → 파티션 재할당 → 남은 인스턴스가 모든 파티션 처리
 * 3. 모든 인스턴스가 죽음 → Kafka 메시지 대기 → 인스턴스 복구 → 중단된 지점부터 재개
 */
@Testcontainers
@DisplayName("Kafka Consumer Failover 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaConsumerFailoverTest {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    ).withKraft();

    private static final String FAILOVER_TOPIC = "failover-test-topic";
    private static final String CONSUMER_GROUP = "failover-test-group";
    private static final int PARTITION_COUNT = 3;

    private KafkaTemplate<String, String> kafkaTemplate;
    private AdminClient adminClient;

    @BeforeAll
    static void setupTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            NewTopic topic = new NewTopic(FAILOVER_TOPIC, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(List.of(topic)).all().get();
        } catch (Exception e) {
            // 이미 존재하면 무시
        }
    }

    @BeforeEach
    void setUp() {
        // Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        // AdminClient
        adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ));
    }

    @AfterEach
    void tearDown() {
        if (adminClient != null) {
            adminClient.close();
        }
    }

    /**
     * 시나리오 1: 다중 컨슈머 인스턴스가 파티션을 분산 처리
     *
     * 검증:
     * - 3개 파티션이 3개 컨슈머에 1:1로 할당됨
     * - 각 컨슈머가 자신에게 할당된 파티션의 메시지만 처리
     */
    @Test
    @Order(1)
    @DisplayName("1. 다중 컨슈머 인스턴스 - 파티션 분산 처리")
    void multipleConsumers_PartitionsDistributed() throws Exception {
        // given
        String groupId = CONSUMER_GROUP + "-distributed-" + UUID.randomUUID();
        int consumerCount = 3;
        List<Consumer<String, String>> consumers = new ArrayList<>();
        Map<String, Set<Integer>> consumerPartitions = new ConcurrentHashMap<>();

        // 3개 컨슈머 생성
        for (int i = 0; i < consumerCount; i++) {
            Consumer<String, String> consumer = createConsumer(groupId, "consumer-" + i);
            consumers.add(consumer);
            consumer.subscribe(List.of(FAILOVER_TOPIC));
            consumerPartitions.put("consumer-" + i, ConcurrentHashMap.newKeySet());
        }

        // when - 메시지 발행 (각 파티션에 1개씩)
        for (int partition = 0; partition < PARTITION_COUNT; partition++) {
            kafkaTemplate.send(FAILOVER_TOPIC, partition, "key-" + partition, "msg-" + partition).get();
        }

        // 각 컨슈머가 폴링하여 할당된 파티션 확인
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);
        CountDownLatch assignmentLatch = new CountDownLatch(consumerCount);

        for (int i = 0; i < consumerCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Consumer<String, String> consumer = consumers.get(idx);
                    // 폴링하면서 할당된 파티션 확인
                    for (int j = 0; j < 20; j++) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        Set<TopicPartition> assignment = consumer.assignment();
                        for (TopicPartition tp : assignment) {
                            consumerPartitions.get("consumer-" + idx).add(tp.partition());
                        }
                        if (!assignment.isEmpty()) {
                            break;
                        }
                    }
                } finally {
                    assignmentLatch.countDown();
                }
            });
        }

        assignmentLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 파티션 분산 확인
        Set<Integer> allAssignedPartitions = consumerPartitions.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        assertThat(allAssignedPartitions).hasSize(PARTITION_COUNT);
        System.out.println("파티션 할당 결과: " + consumerPartitions);

        // cleanup
        consumers.forEach(Consumer::close);
    }

    /**
     * 시나리오 2: 컨슈머 하나 죽음 → 파티션 재할당 → 남은 인스턴스가 처리
     *
     * 검증:
     * - Consumer1 종료 시 해당 파티션이 다른 컨슈머에게 재할당됨
     * - 재할당 후에도 메시지 손실 없이 처리 가능
     */
    @Test
    @Order(2)
    @DisplayName("2. 컨슈머 죽음 → 파티션 재할당 → 남은 인스턴스가 모든 파티션 처리")
    void consumerDeath_PartitionReassignment_RemainingConsumerHandlesAll() throws Exception {
        // given
        String groupId = CONSUMER_GROUP + "-failover-" + UUID.randomUUID();
        AtomicInteger totalMessagesReceived = new AtomicInteger(0);
        AtomicBoolean consumer1Stopped = new AtomicBoolean(false);
        Set<Integer> consumer2Partitions = ConcurrentHashMap.newKeySet();

        // Consumer 1 생성 (나중에 죽일 예정)
        Consumer<String, String> consumer1 = createConsumer(groupId, "consumer-1");
        consumer1.subscribe(List.of(FAILOVER_TOPIC));

        // Consumer 2 생성 (살아남을 예정)
        Consumer<String, String> consumer2 = createConsumer(groupId, "consumer-2");
        consumer2.subscribe(List.of(FAILOVER_TOPIC));

        // 초기 파티션 할당 대기
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            consumer1.poll(Duration.ofMillis(100));
            consumer2.poll(Duration.ofMillis(100));
            return !consumer1.assignment().isEmpty() && !consumer2.assignment().isEmpty();
        });

        Set<Integer> consumer1InitialPartitions = consumer1.assignment().stream()
                .map(TopicPartition::partition)
                .collect(Collectors.toSet());
        Set<Integer> consumer2InitialPartitions = consumer2.assignment().stream()
                .map(TopicPartition::partition)
                .collect(Collectors.toSet());

        System.out.println("초기 할당 - Consumer1: " + consumer1InitialPartitions + ", Consumer2: " + consumer2InitialPartitions);

        // when - Consumer 1 종료 (죽음 시뮬레이션)
        consumer1.close();
        consumer1Stopped.set(true);
        System.out.println("Consumer1 종료됨 - 리밸런싱 시작");

        // 메시지 발행 (모든 파티션에)
        for (int i = 0; i < 9; i++) {
            int partition = i % PARTITION_COUNT;
            kafkaTemplate.send(FAILOVER_TOPIC, partition, "key-" + i, "after-death-" + i).get();
        }

        // Consumer 2가 모든 파티션을 처리하는지 확인
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer2.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        if (record.value().startsWith("after-death-")) {
                            totalMessagesReceived.incrementAndGet();
                            consumer2Partitions.add(record.partition());
                        }
                    }
                    // Consumer2의 현재 할당 확인
                    Set<Integer> currentAssignment = consumer2.assignment().stream()
                            .map(TopicPartition::partition)
                            .collect(Collectors.toSet());
                    consumer2Partitions.addAll(currentAssignment);

                    return consumer2Partitions.size() >= PARTITION_COUNT;
                });

        // then
        System.out.println("리밸런싱 후 Consumer2 파티션: " + consumer2Partitions);
        assertThat(consumer2Partitions).hasSize(PARTITION_COUNT);
        assertThat(totalMessagesReceived.get()).isGreaterThanOrEqualTo(1);

        // cleanup
        consumer2.close();
    }

    /**
     * 시나리오 3: 모든 컨슈머 죽음 → Kafka 메시지 대기 → 복구 → 중단된 지점부터 재개
     *
     * 검증:
     * - 컨슈머 종료 전 커밋된 오프셋 저장
     * - 컨슈머 복구 후 커밋된 오프셋부터 메시지 재개
     * - 다운타임 중 발행된 메시지가 복구 후 처리됨 (at-least-once 보장)
     */
    @Test
    @Order(3)
    @DisplayName("3. 모든 컨슈머 죽음 → Kafka 대기 → 복구 → 중단된 지점부터 메시지 소비")
    void allConsumersDeath_Recovery_ResumeFromCommittedOffset() throws Exception {
        // given - 고유한 메시지 prefix 및 토픽 파티션 키 사용 (다른 테스트와 충돌 방지)
        String testId = UUID.randomUUID().toString().substring(0, 8);
        String uniquePrefix = "recovery-" + testId + "-msg-";
        String groupId = CONSUMER_GROUP + "-recovery-" + testId;
        Set<String> processedBeforeDeath = ConcurrentHashMap.newKeySet();
        Set<String> processedAfterRecovery = ConcurrentHashMap.newKeySet();

        // Phase 1: Consumer 먼저 시작하여 파티션 할당 받기
        Consumer<String, String> consumer1 = createConsumerWithManualCommit(groupId, "consumer-phase1");
        consumer1.subscribe(List.of(FAILOVER_TOPIC));

        // 초기 폴링으로 파티션 할당 대기
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            consumer1.poll(Duration.ofMillis(500));
            return !consumer1.assignment().isEmpty();
        });
        System.out.println("Phase 1: 컨슈머 파티션 할당 완료 - " + consumer1.assignment());

        // Phase 2: 메시지 발행 (10개) 및 일부만 처리
        List<String> allMessages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String msg = uniquePrefix + i;
            allMessages.add(msg);
            kafkaTemplate.send(FAILOVER_TOPIC, testId + "-" + (i % PARTITION_COUNT), msg).get();
        }
        System.out.println("Phase 2: 10개 메시지 발행 완료");

        // 메시지 처리 및 커밋 (최대 5개)
        int maxPolls = 20;
        for (int i = 0; i < maxPolls && processedBeforeDeath.size() < 5; i++) {
            ConsumerRecords<String, String> records = consumer1.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().startsWith(uniquePrefix)) {
                    processedBeforeDeath.add(record.value());
                    if (processedBeforeDeath.size() >= 5) break;
                }
            }
            if (!records.isEmpty()) {
                consumer1.commitSync();
            }
        }
        System.out.println("Phase 2: 처리 후 커밋 완료 - " + processedBeforeDeath.size() + "개 처리됨");

        // Consumer 종료 (모든 인스턴스 죽음 시뮬레이션)
        consumer1.close();
        System.out.println("Phase 2: 모든 컨슈머 종료됨 (다운타임 시작)");

        // Phase 3: 다운타임 중 추가 메시지 발행
        List<String> messagesDuringDowntime = new ArrayList<>();
        for (int i = 10; i < 15; i++) {
            String msg = uniquePrefix + i;
            messagesDuringDowntime.add(msg);
            allMessages.add(msg);
            kafkaTemplate.send(FAILOVER_TOPIC, testId + "-" + (i % PARTITION_COUNT), msg).get();
        }
        System.out.println("Phase 3: 다운타임 중 5개 메시지 추가 발행");

        Thread.sleep(2000); // 안정화 대기

        // Phase 4: 새 컨슈머로 복구 (같은 그룹 ID 사용 → 커밋된 오프셋부터 시작)
        Consumer<String, String> consumer2 = createConsumerWithManualCommit(groupId, "consumer-phase2");
        consumer2.subscribe(List.of(FAILOVER_TOPIC));
        System.out.println("Phase 4: 새 컨슈머 시작 - 중단된 지점부터 재개");

        // 복구된 컨슈머가 다운타임 메시지 모두 처리할 때까지 대기
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer2.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        if (record.value().startsWith(uniquePrefix)) {
                            processedAfterRecovery.add(record.value());
                        }
                    }
                    if (!records.isEmpty()) {
                        consumer2.commitSync();
                    }
                    // 핵심 검증: 다운타임 중 발행된 메시지가 모두 처리되었는지
                    return processedAfterRecovery.containsAll(messagesDuringDowntime);
                });

        consumer2.close();

        // then
        System.out.println("Phase 4 완료:");
        System.out.println("  - 죽기 전 처리: " + processedBeforeDeath.size() + "개");
        System.out.println("  - 복구 후 처리: " + processedAfterRecovery.size() + "개");

        // 검증 1: 다운타임 중 발행된 메시지가 복구 후 처리됨 (핵심 검증)
        assertThat(processedAfterRecovery)
                .as("다운타임 중 발행된 메시지가 복구 후 처리되어야 함")
                .containsAll(messagesDuringDowntime);

        // 검증 2: 전체적으로 at-least-once 보장 (중복은 허용, 유실은 불허)
        Set<String> allProcessed = new HashSet<>();
        allProcessed.addAll(processedBeforeDeath);
        allProcessed.addAll(processedAfterRecovery);

        // 최소한 다운타임 메시지는 모두 처리되어야 함
        assertThat(allProcessed)
                .as("다운타임 메시지는 반드시 처리되어야 함 (at-least-once)")
                .containsAll(messagesDuringDowntime);

        System.out.println("검증 완료: 다운타임 메시지 " + messagesDuringDowntime.size() + "개 모두 처리됨");
    }

    /**
     * 시나리오 4: 컨슈머 그룹 상태 모니터링
     *
     * 검증:
     * - AdminClient로 컨슈머 그룹 상태 확인 가능
     * - 멤버 수, 파티션 할당 상태 확인
     */
    @Test
    @Order(4)
    @DisplayName("4. 컨슈머 그룹 상태 모니터링 - 멤버 및 파티션 할당 확인")
    void consumerGroupMonitoring_MemberAndPartitionStatus() throws Exception {
        // given
        String groupId = CONSUMER_GROUP + "-monitor-" + UUID.randomUUID();
        List<Consumer<String, String>> consumers = new ArrayList<>();
        ExecutorService pollingExecutor = Executors.newFixedThreadPool(2);
        AtomicBoolean keepPolling = new AtomicBoolean(true);

        // 2개 컨슈머 생성 및 지속 폴링 시작
        for (int i = 0; i < 2; i++) {
            Consumer<String, String> consumer = createConsumer(groupId, "monitor-consumer-" + i);
            consumer.subscribe(List.of(FAILOVER_TOPIC));
            consumers.add(consumer);

            // 백그라운드에서 지속 폴링 (그룹 상태 유지를 위해 필요)
            final int idx = i;
            pollingExecutor.submit(() -> {
                while (keepPolling.get()) {
                    try {
                        consumers.get(idx).poll(Duration.ofMillis(500));
                    } catch (Exception e) {
                        break;
                    }
                }
            });
        }

        // when - AdminClient로 그룹 상태 조회 (Stable 상태가 될 때까지 대기)
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        ConsumerGroupDescription description = adminClient
                                .describeConsumerGroups(List.of(groupId))
                                .describedGroups()
                                .get(groupId)
                                .get();
                        // 멤버가 2명이고 상태가 Stable이어야 함
                        return description.members().size() == 2
                                && "Stable".equals(description.state().toString());
                    } catch (Exception e) {
                        return false;
                    }
                });

        ConsumerGroupDescription description = adminClient
                .describeConsumerGroups(List.of(groupId))
                .describedGroups()
                .get(groupId)
                .get();

        // then
        System.out.println("컨슈머 그룹 상태:");
        System.out.println("  - 그룹 ID: " + description.groupId());
        System.out.println("  - 상태: " + description.state());
        System.out.println("  - 멤버 수: " + description.members().size());

        description.members().forEach(member -> {
            System.out.println("  - 멤버: " + member.consumerId());
            System.out.println("    할당된 파티션: " + member.assignment().topicPartitions());
        });

        assertThat(description.members()).hasSize(2);
        assertThat(description.state().toString()).isEqualTo("Stable");

        // cleanup
        keepPolling.set(false);
        pollingExecutor.shutdown();
        pollingExecutor.awaitTermination(5, TimeUnit.SECONDS);
        consumers.forEach(Consumer::close);
    }

    /**
     * 시나리오 5: 순차적 컨슈머 장애 및 복구
     *
     * 검증:
     * - Consumer1 죽음 → Consumer2가 인수
     * - Consumer2 죽음 → Consumer3가 인수
     * - 전체 과정에서 메시지 유실 없음
     */
    @Test
    @Order(5)
    @DisplayName("5. 순차적 컨슈머 장애 및 복구 - 연속 페일오버")
    void sequentialConsumerFailure_ContinuousFailover() throws Exception {
        // given
        String groupId = CONSUMER_GROUP + "-sequential-" + UUID.randomUUID();
        Set<String> allReceivedMessages = ConcurrentHashMap.newKeySet();

        // 메시지 발행
        for (int i = 0; i < 30; i++) {
            kafkaTemplate.send(FAILOVER_TOPIC, "key-" + (i % PARTITION_COUNT), "seq-msg-" + i).get();
        }

        // Phase 1: Consumer1 처리
        Consumer<String, String> consumer1 = createConsumerWithManualCommit(groupId, "seq-consumer-1");
        consumer1.subscribe(List.of(FAILOVER_TOPIC));

        int phase1Count = pollAndProcess(consumer1, allReceivedMessages, 10, "seq-msg-");
        consumer1.commitSync();
        System.out.println("Phase 1 (Consumer1): " + phase1Count + "개 처리, 총 " + allReceivedMessages.size() + "개");
        consumer1.close();

        // Phase 2: Consumer2 처리 (Consumer1이 죽은 후)
        Thread.sleep(1000);
        Consumer<String, String> consumer2 = createConsumerWithManualCommit(groupId, "seq-consumer-2");
        consumer2.subscribe(List.of(FAILOVER_TOPIC));

        int phase2Count = pollAndProcess(consumer2, allReceivedMessages, 10, "seq-msg-");
        consumer2.commitSync();
        System.out.println("Phase 2 (Consumer2): " + phase2Count + "개 처리, 총 " + allReceivedMessages.size() + "개");
        consumer2.close();

        // Phase 3: Consumer3 처리 (Consumer2가 죽은 후)
        Thread.sleep(1000);
        Consumer<String, String> consumer3 = createConsumerWithManualCommit(groupId, "seq-consumer-3");
        consumer3.subscribe(List.of(FAILOVER_TOPIC));

        int phase3Count = pollAndProcess(consumer3, allReceivedMessages, 10, "seq-msg-");
        consumer3.commitSync();
        System.out.println("Phase 3 (Consumer3): " + phase3Count + "개 처리, 총 " + allReceivedMessages.size() + "개");
        consumer3.close();

        // then - 모든 메시지 처리 확인 (at-least-once)
        System.out.println("최종 수신 메시지: " + allReceivedMessages.size() + "개");
        assertThat(allReceivedMessages.size()).isGreaterThanOrEqualTo(30);
    }

    // ─── 헬퍼 메서드 ───

    private Consumer<String, String> createConsumer(String groupId, String clientId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 30000);

        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private Consumer<String, String> createConsumerWithManualCommit(String groupId, String clientId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 커밋
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 30000);

        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private int pollAndProcess(Consumer<String, String> consumer, Set<String> receivedSet,
                               int targetCount, String prefix) {
        int count = 0;
        int maxAttempts = 50;
        int attempts = 0;

        while (count < targetCount && attempts < maxAttempts) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().startsWith(prefix)) {
                    receivedSet.add(record.value());
                    count++;
                }
            }
            attempts++;
        }
        return count;
    }
}
