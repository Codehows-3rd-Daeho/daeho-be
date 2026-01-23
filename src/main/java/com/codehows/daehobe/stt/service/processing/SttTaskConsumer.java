package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.stt.constant.SttRedisKeys;
import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;
import static com.codehows.daehobe.stt.constant.SttRedisKeys.*;

@Slf4j
@Component
public class SttTaskConsumer extends KeyExpirationEventMessageListener {

    private final SttJobProcessor sttJobProcessor;
    private final DistributedLockManager distributedLockManager;

    public SttTaskConsumer(RedisMessageListenerContainer listenerContainer,
                                          DistributedLockManager lockManager,
                                          SttJobProcessor sttJobProcessor) {
        super(listenerContainer);
        this.distributedLockManager = lockManager;
        this.sttJobProcessor = sttJobProcessor;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (!expiredKey.startsWith(SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX)) {
            return;
        }

        String sttIdStr = expiredKey.substring(SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX.length());
        Long sttId = Long.parseLong(sttIdStr);
        String lockKey = SttRedisKeys.ABNORMAL_TERMINATION_LOCK_KEY + ":" + sttId;

        if (!distributedLockManager.acquireLock(lockKey)) {
            log.info("Abnormal termination for STT ID: {} is already being handled. Skipping.", sttId);
            return;
        }

        try {
            log.warn("Heartbeat expired for STT ID: {}. Triggering abnormal termination process.", sttId);
            sttJobProcessor.handleAbnormalTermination(sttId);
        } finally {
            distributedLockManager.releaseLock(lockKey);
        }
    }

    @KafkaListener(
            topics = STT_ENCODING_TOPIC,
            groupId = STT_ENCODING_GROUP,
            containerFactory = "sttEncodingListenerContainerFactory"
    )
    public void listenEncodingTask(@Header(KafkaHeaders.RECEIVED_KEY) String sttIdStr, String message, Acknowledgment acknowledgment) {
        Long sttId = Long.parseLong(sttIdStr);
        String lockKey = ENCODING_LOCK_KEY + ":" + sttId;

        if (!distributedLockManager.acquireLock(lockKey)) {
            log.info("Encoding task for STT ID: {} is already being processed by another instance. Skipping.", sttId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            log.info("Received encoding task for STT ID: {}. Starting processing.", sttId);
            sttJobProcessor.processSingleEncodingJob(sttId);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing encoding task for STT ID: {}", sttId, e);
            // Do not acknowledge, so the message can be re-processed
            throw new RuntimeException(e);
        } finally {
            distributedLockManager.releaseLock(lockKey);
        }
    }

    @RetryableTopic(
            attempts = "1000", // Max retries: 1000
            backoff = @Backoff(delay = 2000, multiplier = 1.0), // Fixed delay of 2 seconds
            include = SttNotCompletedException.class, // Only retry on this specific exception
            dltStrategy = DltStrategy.FAIL_ON_ERROR // After max attempts, send to DLT if still failing
    )
    @KafkaListener(
            topics = STT_PROCESSING_TOPIC,
            groupId = STT_PROCESSING_GROUP,
            containerFactory = "sttProcessingListenerContainerFactory"
    )
    public void listenProcessingTask(@Header(KafkaHeaders.RECEIVED_KEY) String sttIdStr, String message, Acknowledgment acknowledgment) {
        Long sttId = Long.parseLong(sttIdStr);
        String lockKey = PROCESSING_LOCK_KEY + ":" + sttId;

        if (!distributedLockManager.acquireLock(lockKey)) {
            log.info("Processing task for STT ID: {} is locked. Skipping.", sttId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            log.info("Received processing task for STT ID: {}. Starting processing.", sttId);
            sttJobProcessor.processSingleSttJob(sttId);
            acknowledgment.acknowledge();
        } catch (SttNotCompletedException e) {
            acknowledgment.acknowledge(); // Acknowledge before re-throwing for retry
            throw e;
        } catch (Exception e) {
            log.error("Error processing STT task for STT ID: {}", sttId, e);
            throw new RuntimeException(e);
        } finally {
            distributedLockManager.releaseLock(lockKey);
        }
    }

    @RetryableTopic(
            attempts = "1000", // Max retries: 500
            backoff = @Backoff(delay = 2000, multiplier = 1.0), // Fixed delay of 10 seconds
            include = SttNotCompletedException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = STT_SUMMARIZING_TOPIC,
            groupId = STT_SUMMARIZING_GROUP,
            containerFactory = "sttSummarizingListenerContainerFactory"
    )
    public void listenSummarizingTask(@Header(KafkaHeaders.RECEIVED_KEY) String sttIdStr, String message, Acknowledgment acknowledgment) {
        Long sttId = Long.parseLong(sttIdStr);
        String lockKey = SUMMARIZING_LOCK_KEY + ":" + sttId;

        if (!distributedLockManager.acquireLock(lockKey)) {
            log.info("Summarizing task for STT ID: {} is locked. Skipping.", sttId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            log.info("Received summarizing task for STT ID: {}. Starting processing.", sttId);
            sttJobProcessor.processSingleSummaryJob(sttId);
            acknowledgment.acknowledge();
        } catch (SttNotCompletedException e) {
            acknowledgment.acknowledge(); // Acknowledge before re-throwing for retry
            throw e;
        } catch (Exception e) {
            log.error("Error processing summarizing task for STT ID: {}", sttId, e);
            throw new RuntimeException(e);
        } finally {
            distributedLockManager.releaseLock(lockKey);
        }
    }
}