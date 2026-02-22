package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.stt.constant.SttRedisKeys;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.STTService;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final STTService sttService;
    private final StringRedisTemplate redisTemplate;

    @Value("${stt.recording.orphan-threshold-hours:3}")
    private long orphanThresholdHours;

    @Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
    public void pollProcessingTasks() {
        Set<Long> taskIds = getTaskIdsWithFallback(STT.Status.PROCESSING);

        for (Long sttId : taskIds) {
            sttJobProcessor.processSingleSttJob(sttId);
        }
    }

    @Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
    public void pollSummarizingTasks() {
        Set<Long> taskIds = getTaskIdsWithFallback(STT.Status.SUMMARIZING);

        for (Long sttId : taskIds) {
            sttJobProcessor.processSingleSummaryJob(sttId);
        }
    }

    @Scheduled(fixedDelayString = "${stt.recording.safety-net-interval-ms:60000}")
    public void scanOrphanedRecordingTasks() {
        Set<Long> recordingIds = sttRepository.findIdsByStatus(STT.Status.RECORDING);
        for (Long sttId : recordingIds) {
            try {
                String heartbeatKey = SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX + sttId;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey))) continue; // 정상 진행 중

                STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
                if (cachedStatus != null) {
                    // Redis 정상: heartbeat만 없는 실제 고아
                    if (cachedStatus.getStatus() == STT.Status.RECORDING) {
                        log.warn("[SafetyNet] Confirmed orphan sttId={}. Triggering recovery.", sttId);
                        sttService.handleAbnormalTermination(sttId);
                    }
                } else {
                    // 캐시도 없음: Redis 재시작 후 키 소실 가능성
                    // DB에서 생성 시각 확인, 임계 초과분만 복구 (2차 안전망)
                    sttService.handleAbnormalTerminationIfStuck(sttId, orphanThresholdHours);
                }
            } catch (Exception e) {
                log.error("[SafetyNet] Error checking sttId={}. Continuing.", sttId, e);
            }
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
