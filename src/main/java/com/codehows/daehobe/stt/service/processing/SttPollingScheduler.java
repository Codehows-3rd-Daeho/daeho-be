package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SttPollingScheduler {

    private final STTRepository sttRepository;
    private final SttJobProcessor sttJobProcessor;
    private final SttCacheService sttCacheService;

    @Value("${stt.polling.max-attempts:150}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
    public void pollProcessingTasks() {
        Set<Long> taskIds = getTaskIdsWithFallback(STT.Status.PROCESSING);

        for (Long sttId : taskIds) {
            try {
                sttJobProcessor.processSingleSttJob(sttId);
                // 성공 시 processor에서 이미 polling set 전환됨
            } catch (SttNotCompletedException e) {
                // Redis에서 retry count 증가
                int retryCount = sttCacheService.incrementRetryCount(sttId);
                if (retryCount >= maxAttempts) {
                    handleMaxRetryExceeded(sttId, STT.Status.PROCESSING);
                } else {
                    log.debug("STT {} not completed, retry count: {}", sttId, retryCount);
                }
            } catch (Exception e) {
                log.error("Error processing STT job for ID: {}", sttId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
    public void pollSummarizingTasks() {
        Set<Long> taskIds = getTaskIdsWithFallback(STT.Status.SUMMARIZING);

        for (Long sttId : taskIds) {
            try {
                sttJobProcessor.processSingleSummaryJob(sttId);
                // 성공 시 processor에서 이미 polling set에서 제거됨
            } catch (SttNotCompletedException e) {
                // Redis에서 retry count 증가
                int retryCount = sttCacheService.incrementRetryCount(sttId);
                if (retryCount >= maxAttempts) {
                    handleMaxRetryExceeded(sttId, STT.Status.SUMMARIZING);
                } else {
                    log.debug("Summary {} not completed, retry count: {}", sttId, retryCount);
                }
            } catch (Exception e) {
                log.error("Error processing summary job for ID: {}", sttId, e);
            }
        }
    }

    private void handleMaxRetryExceeded(Long sttId, STT.Status currentStatus) {
        log.warn("Max retry attempts exceeded for STT {}. Removing from polling set.", sttId);
        sttCacheService.removeFromPollingSet(sttId, currentStatus);
        sttCacheService.resetRetryCount(sttId);

        // 캐시 상태 업데이트 (실패 표시)
        STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
        if (cachedStatus != null) {
            // ENCODED 상태로 롤백 (사용자가 재시도 가능하도록)
            cachedStatus.updateStatus(STT.Status.ENCODED);
            sttCacheService.cacheSttStatus(cachedStatus);
        }
    }

    private Set<Long> getTaskIdsWithFallback(STT.Status status) {
        Set<Long> taskIds = sttCacheService.getPollingTaskIds(status);

        if (taskIds.isEmpty() && !sttCacheService.isRedisAvailable()) {
            try {
                taskIds = sttRepository.findIdsByStatus(status);
                log.warn("Redis unavailable, falling back to DB for {} polling. Found {} tasks",
                        status, taskIds.size());
            } catch (Exception e) {
                log.error("Both Redis and DB unavailable for polling", e);
            }
        }

        return taskIds;
    }
}
