package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.utils.DataSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttTaskProcessor {

    private final STTRepository sttRepository;
    private final STTService sttService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Lazy
    @Autowired
    private SttTaskProcessor self;

    public static final String STT_PROCESSING_SET = "stt:processing";
    public static final String STT_SUMMARIZING_SET = "stt:summarizing";
    public static final String STT_STATUS_HASH_PREFIX = "stt:status:";
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Async
    public void startSmartLoop() {
        if (isProcessing.compareAndSet(false, true)) {
            log.info("Starting STT smart-loop processor...");
            scheduler.scheduleWithFixedDelay(this::processAllTasks, 0, 2, TimeUnit.SECONDS);
        } else {
            log.info("Processing is already in progress.");
        }
    }

    private void processAllTasks() {
        if (redisTemplate.opsForSet().size(STT_PROCESSING_SET) == 0 &&
                        redisTemplate.opsForSet().size(STT_SUMMARIZING_SET) == 0){
            log.info("Stopping STT smart-loop processor...");
            scheduler.shutdownNow();
            isProcessing.set(false);
        }

        try {
            log.info("loop processor started.");
            log.info("Starting STT task processing cycle.");
            // Process STT Queue
            Set<String> processingSttIds = redisTemplate.opsForSet().members(STT_PROCESSING_SET);
            if (processingSttIds != null && !processingSttIds.isEmpty()) {
                log.info("Processing {} tasks from STT queue.", processingSttIds.size());
                for (String sttIdObj : processingSttIds) {
                    try {
                        self.processSingleSttJob(Long.valueOf(sttIdObj));
                    } catch (Exception e) {
                        log.error("Error processing STT task for sttId: {}", sttIdObj, e);
                    }
                }
            }

            Set<String> summarizingSttIds = redisTemplate.opsForSet().members(STT_SUMMARIZING_SET);
            if (summarizingSttIds != null && !summarizingSttIds.isEmpty()) {
                log.info("Processing {} tasks from summary queue.", summarizingSttIds.size());
                for (String sttIdObj : summarizingSttIds) {
                    try {
                        self.processSingleSummaryJob(Long.valueOf(sttIdObj));
                    } catch (Exception e) {
                        log.error("Error processing summary task for sttId: {}", sttIdObj, e);
                    }
                }
            }

            log.info("Finished STT task processing cycle. Waiting for next interval.");
        } finally {
            isProcessing.set(false);
            log.info("All task queues are empty. Smart-loop processor going idle.");
        }
    }

    @Transactional
    public void processSingleSttJob(Long sttId) {
        STT stt = sttRepository.findById(sttId).orElse(null);
        if (stt == null || stt.getStatus() != STT.Status.PROCESSING) {
            redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
            return;
        }

        STTResponseDto result = sttService.checkSTTStatus(stt);
        stt.updateContent(result.getContent());

        STTDto cachedStt = STTDto.fromEntity(stt);
        cachedStt.updateProgress(result.getProgress());
        self.cacheSttStatus(cachedStt);

        if (result.isCompleted()) {
            log.info("STT for sttId: {} is completed. Requesting summary.", sttId);
            stt.setStatus(STT.Status.SUMMARIZING);
            stt.updateContent(result.getContent());
            sttRepository.save(stt);

            STTDto finalCachedStt = STTDto.fromEntity(stt);
            finalCachedStt.updateProgress(result.getProgress());
            self.cacheSttStatus(finalCachedStt);

            sttService.requestSummary(sttId);
            redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
            redisTemplate.opsForSet().add(STT_SUMMARIZING_SET, String.valueOf(sttId));
        }
    }

    @Transactional
    public void processSingleSummaryJob(Long sttId) {
        STT stt = sttRepository.findById(sttId).orElse(null);
        if (stt == null || stt.getStatus() != STT.Status.SUMMARIZING || stt.getSummaryRid() == null) {
            redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
            redisTemplate.delete(STT_STATUS_HASH_PREFIX + sttId);
            return;
        }

        SummaryResponseDto result = sttService.checkSummaryStatus(stt);
        stt.updateSummary(result.getSummaryText());

        STTDto cachedStt = STTDto.fromEntity(stt);
        cachedStt.updateProgress(result.getProgress());
        self.cacheSttStatus(cachedStt);

        if (result.isCompleted()) {
            log.info("Summary for sttId: {} is completed.", sttId);
            stt.setStatus(STT.Status.COMPLETED);
            stt.updateSummary(result.getSummaryText());
            sttRepository.save(stt);

            STTDto finalCachedStt = STTDto.fromEntity(stt);
            finalCachedStt.updateProgress(result.getProgress());
            self.cacheSttStatus(finalCachedStt);

            redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
            redisTemplate.expire(STT_STATUS_HASH_PREFIX + sttId, 5, TimeUnit.MINUTES);
        }
    }

    public void cacheSttStatus(STTDto stt) {
        redisTemplate.opsForValue().set(STT_STATUS_HASH_PREFIX + stt.getId(), DataSerializer.serialize(stt));
    }
}
