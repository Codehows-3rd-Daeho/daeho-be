package com.codehows.daehobe.stt.service.processor;

import com.codehows.daehobe.stt.service.constant.SttTaskType;
import com.codehows.daehobe.stt.service.scheduler.AbnormalTerminationScheduler;
import com.codehows.daehobe.stt.service.scheduler.EncodingScheduler;
import com.codehows.daehobe.stt.service.scheduler.ProcessingScheduler;
import com.codehows.daehobe.stt.service.scheduler.SummarizingScheduler;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SttTaskConsumer {

    private final EncodingScheduler encodingScheduler;
    private final ProcessingScheduler processingScheduler;
    private final SummarizingScheduler summarizingScheduler;
    private final AbnormalTerminationScheduler abnormalTerminationScheduler;

    @KafkaListener(
            topics = STT_ABNORMAL_TERMINATION_TOPIC,
            groupId = STT_ABNORMAL_TERMINATION_GROUP,
            containerFactory = "sttAbnormalTerminationListenerContainerFactory"
    )
    public void listenAbnormalTerminationTask(@Header(KafkaHeaders.RECEIVED_KEY) String id, String message) {
        log.info("Starting scheduler for task: {}", message);
        abnormalTerminationScheduler.start();
    }

    @KafkaListener(
            topics = STT_ENCODING_TOPIC,
            groupId = STT_ENCODING_GROUP,
            containerFactory = "sttEncodingListenerContainerFactory"
    )
    public void listenEncodingTask(@Header(KafkaHeaders.RECEIVED_KEY) String id, String message) {
        log.info("Starting scheduler for task: {}", message);
        encodingScheduler.start();
    }

    @KafkaListener(
            topics = STT_PROCESSING_TOPIC,
            groupId = STT_PROCESSING_GROUP,
            containerFactory = "sttProcessingListenerContainerFactory"
    )
    public void listenProcessingTask(@Header(KafkaHeaders.RECEIVED_KEY) String id, String message) {
        log.info("Starting scheduler for task: {}", message);
        processingScheduler.start();
    }

    @KafkaListener(
            topics = STT_SUMMARIZING_TOPIC,
            groupId = STT_SUMMARIZING_GROUP,
            containerFactory = "sttSummarizingListenerContainerFactory"
    )
    public void listenSummarizingTask(@Header(KafkaHeaders.RECEIVED_KEY) String id, String message) {
        log.info("Starting scheduler for task: {}", message);
        summarizingScheduler.start();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all STT schedulers...");
        abnormalTerminationScheduler.shutdown();
        encodingScheduler.shutdown();
        processingScheduler.shutdown();
        summarizingScheduler.shutdown();
    }
}