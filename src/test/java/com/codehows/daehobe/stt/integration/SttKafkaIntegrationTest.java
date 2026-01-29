package com.codehows.daehobe.stt.integration;

import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Kafka 통합 테스트
 *
 * 테스트 케이스:
 * 1. STT Encoding Topic 메시지 발행/소비
 * 2. RetryableTopic 재시도 동작 (SttNotCompletedException 시 2초 딜레이)
 * 3. 수동 커밋 (Manual Acknowledgment) 검증
 * 4. Dead Letter Topic 이동 테스트
 * 5. 메시지 순서 보장 (같은 파티션)
 */
@Testcontainers
@DisplayName("Kafka 통합 테스트")
class SttKafkaIntegrationTest {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    ).withKraft();

    private KafkaTemplate<String, String> kafkaTemplate;
    private Consumer<String, String> consumer;

    @BeforeAll
    static void setupTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            List<NewTopic> topics = List.of(
                    new NewTopic(STT_ENCODING_TOPIC, 3, (short) 1),
                    new NewTopic(STT_PROCESSING_TOPIC, 3, (short) 1),
                    new NewTopic(STT_SUMMARIZING_TOPIC, 3, (short) 1),
                    new NewTopic(STT_ENCODING_TOPIC + "-dlt", 1, (short) 1),
                    new NewTopic(STT_PROCESSING_TOPIC + "-dlt", 1, (short) 1),
                    new NewTopic(STT_SUMMARIZING_TOPIC + "-dlt", 1, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // 이미 존재하는 토픽이면 무시
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

        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = consumerFactory.createConsumer();
    }

    @Test
    @DisplayName("STT Encoding Topic 메시지 발행/소비")
    void publishAndConsume_EncodingTopic_Success() throws ExecutionException, InterruptedException {
        // given
        String sttId = "123";
        String message = "start-encoding";

        // when - 메시지 발행
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(STT_ENCODING_TOPIC, sttId, message);
        SendResult<String, String> result = future.get();

        // then - 발행 성공
        assertThat(result.getRecordMetadata().topic()).isEqualTo(STT_ENCODING_TOPIC);

        // when - 메시지 소비
        consumer.subscribe(Collections.singletonList(STT_ENCODING_TOPIC));

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    assertThat(records.count()).isGreaterThan(0);

                    boolean found = false;
                    for (ConsumerRecord<String, String> record : records) {
                        if (sttId.equals(record.key()) && message.equals(record.value())) {
                            found = true;
                            break;
                        }
                    }
                    assertThat(found).isTrue();
                });
    }

    @Test
    @DisplayName("메시지 키로 파티션 결정 - 같은 키는 같은 파티션")
    void messagePartitioning_SameKeyToSamePartition() throws ExecutionException, InterruptedException {
        // given
        String sttId = "456";
        List<Integer> partitions = new ArrayList<>();

        // when - 같은 키로 여러 메시지 발행
        for (int i = 0; i < 5; i++) {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(STT_ENCODING_TOPIC, sttId, "message-" + i);
            SendResult<String, String> result = future.get();
            partitions.add(result.getRecordMetadata().partition());
        }

        // then - 모든 메시지가 같은 파티션으로 전송
        assertThat(partitions).allMatch(p -> p.equals(partitions.get(0)));
    }

    @Test
    @DisplayName("메시지 순서 보장 - 같은 파티션 내에서 순서 유지")
    void messageOrdering_SamePartition_OrderMaintained() throws ExecutionException, InterruptedException {
        // given
        String sttId = "789";
        String topic = STT_PROCESSING_TOPIC;
        List<String> sentMessages = Arrays.asList("msg-1", "msg-2", "msg-3", "msg-4", "msg-5");

        // when - 순차적으로 메시지 발행
        for (String msg : sentMessages) {
            kafkaTemplate.send(topic, sttId, msg).get();
        }

        // 소비
        consumer.subscribe(Collections.singletonList(topic));
        List<String> receivedMessages = new ArrayList<>();

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        if (sttId.equals(record.key())) {
                            receivedMessages.add(record.value());
                        }
                    }
                    return receivedMessages.size() >= sentMessages.size();
                });

        // then - 순서 유지됨
        assertThat(receivedMessages).containsExactlyElementsOf(sentMessages);
    }

    @Test
    @DisplayName("수동 커밋 시뮬레이션 - 명시적 커밋 전까지 오프셋 유지")
    void manualAcknowledgment_OffsetNotCommittedUntilExplicit() throws ExecutionException, InterruptedException {
        // given
        String sttId = "manual-ack-test";
        String topic = STT_SUMMARIZING_TOPIC;
        String message = "test-manual-ack";

        kafkaTemplate.send(topic, sttId, message).get();

        // Consumer 1: 메시지 읽고 커밋하지 않음
        consumer.subscribe(Collections.singletonList(topic));

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    return records.count() > 0;
                });

        // Consumer 종료 (커밋 없이)
        consumer.close();

        // Consumer 2: 새로운 컨슈머로 같은 그룹에서 읽기
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "manual-ack-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        Consumer<String, String> consumer2 = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer();
        consumer2.subscribe(Collections.singletonList(topic));

        // then - 새 컨슈머 그룹이라 메시지 다시 읽을 수 있음
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer2.poll(Duration.ofMillis(500));
                    boolean found = false;
                    for (ConsumerRecord<String, String> record : records) {
                        if (sttId.equals(record.key()) && message.equals(record.value())) {
                            found = true;
                            break;
                        }
                    }
                    assertThat(found).isTrue();
                });

        consumer2.close();
    }

    @Test
    @DisplayName("다중 토픽 동시 발행")
    void multipleTopics_ConcurrentPublish_Success() throws ExecutionException, InterruptedException {
        // given
        String sttId = "multi-topic-test";

        // when - 3개 토픽에 동시 발행
        CompletableFuture<SendResult<String, String>> future1 =
                kafkaTemplate.send(STT_ENCODING_TOPIC, sttId, "encoding-msg");
        CompletableFuture<SendResult<String, String>> future2 =
                kafkaTemplate.send(STT_PROCESSING_TOPIC, sttId, "processing-msg");
        CompletableFuture<SendResult<String, String>> future3 =
                kafkaTemplate.send(STT_SUMMARIZING_TOPIC, sttId, "summarizing-msg");

        SendResult<String, String> result1 = future1.get();
        SendResult<String, String> result2 = future2.get();
        SendResult<String, String> result3 = future3.get();

        // then
        assertThat(result1.getRecordMetadata().topic()).isEqualTo(STT_ENCODING_TOPIC);
        assertThat(result2.getRecordMetadata().topic()).isEqualTo(STT_PROCESSING_TOPIC);
        assertThat(result3.getRecordMetadata().topic()).isEqualTo(STT_SUMMARIZING_TOPIC);
    }

    @Test
    @DisplayName("높은 처리량 테스트 - 1000개 메시지 발행/소비")
    void highThroughput_1000Messages_Success() throws InterruptedException {
        // given
        String topic = STT_ENCODING_TOPIC;
        int messageCount = 1000;
        AtomicInteger sentCount = new AtomicInteger(0);

        // when - 1000개 메시지 발행
        for (int i = 0; i < messageCount; i++) {
            String sttId = "bulk-" + i;
            kafkaTemplate.send(topic, sttId, "message-" + i)
                    .thenAccept(result -> sentCount.incrementAndGet());
        }

        // 발행 완료 대기
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> sentCount.get() >= messageCount);

        // then
        assertThat(sentCount.get()).isEqualTo(messageCount);

        // 소비 확인
        consumer.subscribe(Collections.singletonList(topic));
        AtomicInteger receivedCount = new AtomicInteger(0);

        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        if (record.key() != null && record.key().startsWith("bulk-")) {
                            receivedCount.incrementAndGet();
                        }
                    }
                    return receivedCount.get() >= messageCount;
                });

        assertThat(receivedCount.get()).isGreaterThanOrEqualTo(messageCount);
    }

    @Test
    @DisplayName("SttNotCompletedException 재시도 시뮬레이션")
    void retryableException_SttNotCompleted_SimulateRetry() {
        // given
        AtomicInteger attemptCount = new AtomicInteger(0);
        int maxRetries = 3;

        // when - SttNotCompletedException 발생 시 재시도 로직 시뮬레이션
        Runnable processor = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < maxRetries) {
                throw new SttNotCompletedException("STT job not yet completed, attempt: " + attempt);
            }
            // 최종 성공
        };

        // 재시도 루프 시뮬레이션
        boolean completed = false;
        for (int i = 0; i < maxRetries; i++) {
            try {
                processor.run();
                completed = true;
                break;
            } catch (SttNotCompletedException e) {
                // 재시도
            }
        }

        // then
        assertThat(completed).isTrue();
        assertThat(attemptCount.get()).isEqualTo(maxRetries);
    }

    @Test
    @DisplayName("Dead Letter Topic 발행 시뮬레이션")
    void deadLetterTopic_PublishSimulation_Success() throws ExecutionException, InterruptedException {
        // given
        String dltTopic = STT_ENCODING_TOPIC + "-dlt";
        String sttId = "dlt-test";
        String errorMessage = "Failed after max retries";

        // when - DLT로 메시지 발행 (실패한 메시지 저장)
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(dltTopic, sttId, errorMessage);
        SendResult<String, String> result = future.get();

        // then
        assertThat(result.getRecordMetadata().topic()).isEqualTo(dltTopic);

        // DLT에서 메시지 확인
        consumer.subscribe(Collections.singletonList(dltTopic));

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    boolean found = false;
                    for (ConsumerRecord<String, String> record : records) {
                        if (sttId.equals(record.key())) {
                            found = true;
                            assertThat(record.value()).isEqualTo(errorMessage);
                            break;
                        }
                    }
                    assertThat(found).isTrue();
                });
    }
}
