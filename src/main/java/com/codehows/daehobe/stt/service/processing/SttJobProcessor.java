package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttJobProcessor {

    private final STTRepository sttRepository;
    @Qualifier("dagloSttProvider")
    private final SttProvider sttProvider;
    private final SttCacheService sttCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${stt.polling.max-attempts:150}")
    private int maxAttempts;

    @Async(value = "sttTaskExecutor")
    @Transactional
    public void processSingleSttJob(Long sttId) {
        STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);

        if (cachedStatus == null) {
            log.warn("STT job {} not found in cache. Skipping.", sttId);
            return;
        }

        if(cachedStatus.getStatus() != STT.Status.PROCESSING || cachedStatus.getRid() == null) {
            log.warn("STT job {} is not in PROCESSING state. Skipping.", sttId);
            return;
        }

        try {
            SttTranscriptionResult result = sttProvider.checkTranscriptionStatus(cachedStatus.getRid());

            if (result == null) {
                log.error("STT status check for rid {} returned null", cachedStatus.getRid());
                throw new RuntimeException("STT status check returned null");
            }

            cachedStatus.updateContent(result.getContent());
            cachedStatus.updateProgress(result.getProgress());
            sttCacheService.cacheSttStatus(cachedStatus);
            messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            if (result.isCompleted()) {
                log.info("STT {} completed, transitioning to SUMMARIZING", sttId);

                String summaryRid = sttProvider.requestSummary(result.getContent());

                cachedStatus.updateStatus(STT.Status.SUMMARIZING);
                cachedStatus.updateSummaryRid(summaryRid);
                cachedStatus.updateRetryCount(0);
                sttCacheService.cacheSttStatus(cachedStatus);
                messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

                // Redis-only: polling set 전환 (DB 저장 제거)
                sttCacheService.removeFromPollingSet(sttId, STT.Status.PROCESSING);
                sttCacheService.addToPollingSet(sttId, STT.Status.SUMMARIZING);
                sttCacheService.resetRetryCount(sttId);
            } else {
                log.info("STT {} is still in progress ({}%). Will retry.", sttId, result.getProgress());
                int retryCount = sttCacheService.incrementRetryCount(sttId);
                if (retryCount >= maxAttempts) {
                    handleMaxRetryExceeded(sttId, STT.Status.PROCESSING);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process STT job for sttId: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                handleMaxRetryExceeded(sttId, STT.Status.PROCESSING);
            }
        }
    }

    @Async(value = "sttTaskExecutor")
    @Transactional
    public void processSingleSummaryJob(Long sttId) {
        STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);

        if (cachedStatus == null) {
            log.warn("STT summary job {} not found in cache. Skipping.", sttId);
            return;
        }

        if (cachedStatus.getStatus() != STT.Status.SUMMARIZING || cachedStatus.getSummaryRid() == null) {
            log.warn("STT summary job {} is not in SUMMARIZING state. Skipping.", sttId);
            return;
        }

        try {
            SttSummaryResult result = sttProvider.checkSummaryStatus(cachedStatus.getSummaryRid());

            if (result == null) {
                log.error("Summary status check for rid {} returned null", cachedStatus.getSummaryRid());
                throw new RuntimeException("Summary status check returned null");
            }

            cachedStatus.updateSummary(result.getSummaryText());
            cachedStatus.updateProgress(result.getProgress());
            sttCacheService.cacheSttStatus(cachedStatus);
            messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            if (result.isCompleted()) {
                log.info("Summary for sttId {} completed", sttId);
                cachedStatus.updateStatus(STT.Status.COMPLETED);
                sttCacheService.cacheSttStatus(cachedStatus);
                messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

                // polling set에서 제거 및 retry count 정리
                sttCacheService.removeFromPollingSet(sttId, STT.Status.SUMMARIZING);
                sttCacheService.resetRetryCount(sttId);

                // COMPLETED에서 최종 DB 저장
                STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                stt.updateFromDto(cachedStatus);
                sttRepository.save(stt);
            } else {
                log.info("STT summary {} is still in progress ({}%). Will retry.", sttId, result.getProgress());
                int retryCount = sttCacheService.incrementRetryCount(sttId);
                if (retryCount >= maxAttempts) {
                    handleMaxRetryExceeded(sttId, STT.Status.SUMMARIZING);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process summary job for sttId: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                handleMaxRetryExceeded(sttId, STT.Status.SUMMARIZING);
            }
        }
    }

    private void handleMaxRetryExceeded(Long sttId, STT.Status currentStatus) {
        log.warn("Max retry attempts exceeded for STT {}. Removing from polling set.", sttId);
        sttCacheService.removeFromPollingSet(sttId, currentStatus);
        sttCacheService.resetRetryCount(sttId);

        STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
        if (cachedStatus != null) {
            cachedStatus.updateStatus(STT.Status.ENCODED); // ENCODED로 롤백 (사용자 재시도 가능)
            sttCacheService.cacheSttStatus(cachedStatus);
        }
    }

    private boolean isUnrecoverableError(Exception e) {
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e.getCause() instanceof NumberFormatException;
    }
}
