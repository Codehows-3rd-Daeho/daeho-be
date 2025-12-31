package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.utils.DataSerializer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.service.stt.SttTaskProcessor.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttJobExecutor {

    private final STTRepository sttRepository;
    private final STTService sttService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void processSingleSttJob(Long sttId) {
        try {
            String hashKey = STT_STATUS_HASH_PREFIX + sttId;
            STTDto cachedStatus = DataSerializer.deserialize(redisTemplate.opsForValue().get(hashKey), STTDto.class);
            if (cachedStatus == null) {
                log.warn("STT not found: {}", sttId);
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_PROCESSING_SET, String.valueOf(sttId));
                return;
            }

            if (cachedStatus.getStatus() != STT.Status.PROCESSING) {
                log.info("STT {} status changed to {}, removing from queue", sttId, cachedStatus.getStatus());
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_PROCESSING_SET, String.valueOf(sttId));
                return;
            }

            STTResponseDto result = sttService.checkSTTStatus(cachedStatus.getRid());
            cachedStatus.updateContent(result.getContent());
            cachedStatus.updateProgress(result.getProgress());
            cacheSttStatus(cachedStatus);

            if (result.isCompleted()) {
                log.info("STT {} completed, transitioning to SUMMARIZING", sttId);
                cachedStatus.updateStatus(STT.Status.SUMMARIZING);
                cachedStatus.updateContent(result.getContent());
                cachedStatus.updateProgress(result.getProgress());
                String summaryRid = sttService.requestSummary(result.getContent());
                cachedStatus.updateSummaryRid(summaryRid);
                cacheSttStatus(cachedStatus);

                redisTemplate.execute((RedisCallback<Object>) connection -> {
                    connection.sRem(SttTaskProcessor.STT_PROCESSING_SET.getBytes(), String.valueOf(sttId).getBytes());
                    connection.sAdd(SttTaskProcessor.STT_SUMMARIZING_SET.getBytes(), String.valueOf(sttId).getBytes());
                    return null;
                });

                STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                stt.updateFromDto(cachedStatus);
            }
        } catch (Exception e) {
            log.error("Failed to process STT job: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_PROCESSING_SET, String.valueOf(sttId));
            }
        }
    }

    @Transactional
    public void processSingleSummaryJob(Long sttId) {
        try {
            String hashKey = STT_STATUS_HASH_PREFIX + sttId;
            STTDto cachedStatus = DataSerializer.deserialize(redisTemplate.opsForValue().get(hashKey), STTDto.class);
            if (cachedStatus == null) {
                log.warn("STT not found: {}", sttId);
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_SUMMARIZING_SET, String.valueOf(sttId));
                redisTemplate.delete(SttTaskProcessor.STT_STATUS_HASH_PREFIX + sttId);
                return;
            }

            if (cachedStatus.getStatus() != STT.Status.SUMMARIZING) {
                log.info("STT {} status changed to {}, removing from queue", sttId, cachedStatus.getStatus());
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_SUMMARIZING_SET, String.valueOf(sttId));
                return;
            }

            if (cachedStatus.getSummaryRid() == null) {
                log.warn("STT {} has no summaryRid", sttId);
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_SUMMARIZING_SET, String.valueOf(sttId));
                return;
            }

            SummaryResponseDto result = sttService.checkSummaryStatus(cachedStatus.getSummaryRid());
            cachedStatus.updateSummary(result.getSummaryText());
            cachedStatus.updateProgress(result.getProgress());
            cacheSttStatus(cachedStatus);

            if (result.isCompleted()) {
                log.info("Summary {} completed", sttId);
                cachedStatus.updateStatus(STT.Status.COMPLETED);
                cachedStatus.updateSummary(result.getSummaryText());
                cachedStatus.updateProgress(result.getProgress());
                cacheSttStatus(cachedStatus);

                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_SUMMARIZING_SET, String.valueOf(sttId));
                redisTemplate.expire(SttTaskProcessor.STT_STATUS_HASH_PREFIX + sttId, 5, TimeUnit.MINUTES);

                STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                stt.updateFromDto(cachedStatus);
            }
        } catch (Exception e) {
            log.error("Failed to process summary job: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_SUMMARIZING_SET, String.valueOf(sttId));
            }
        }
    }

    public void cacheSttStatus(STTDto stt) {
        try {
            redisTemplate.opsForValue().set(
                    SttTaskProcessor.STT_STATUS_HASH_PREFIX + stt.getId(),
                    DataSerializer.serialize(stt),
                    30,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("Failed to cache STT status: {}", stt.getId(), e);
        }
    }

    private boolean isUnrecoverableError(Exception e) {
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e.getCause() instanceof NumberFormatException;
    }
}