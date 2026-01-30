package com.codehows.daehobe.stt.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SttEncodingTaskExecutor {

    private final SttJobProcessor sttJobProcessor;

    @Async("sttTaskExecutor")
    public void submitEncodingTask(Long sttId) {
        log.info("Starting async encoding task for STT ID: {}", sttId);
        sttJobProcessor.processSingleEncodingJob(sttId);
    }

    @Async("sttTaskExecutor")
    public void submitAbnormalTermination(Long sttId) {
        log.warn("Starting async abnormal termination for STT ID: {}", sttId);
        sttJobProcessor.handleAbnormalTermination(sttId);
    }
}
