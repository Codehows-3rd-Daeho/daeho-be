package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.utils.DataSerializer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.service.stt.SttTaskProcessor.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttJobExecutor {

    private final STTRepository sttRepository;
    private final STTService sttService;
    private final FileService fileService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long ABNORMAL_TERMINATION_TIMEOUT_MS = 30000; // 30초
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();


    @Transactional
    public void handleAbnormalTermination(Long sttId) {
        Object lastChunkTimestampObj = redisTemplate.opsForHash().get(SttTaskProcessor.STT_LAST_CHUNK_TIMESTAMP_HASH, String.valueOf(sttId));
        if (lastChunkTimestampObj == null) {
            return;
        }

        long lastChunkTimestamp = Long.parseLong((String) lastChunkTimestampObj);
        if (System.currentTimeMillis() - lastChunkTimestamp > ABNORMAL_TERMINATION_TIMEOUT_MS) {
            log.warn("Abnormal termination detected for STT ID: {}. Starting recovery process.", sttId);

            STT stt = sttRepository.findById(sttId).orElse(null);
            if (stt == null) {
                log.error("STT entity not found for abnormally terminated session: {}", sttId);
                // Clean up Redis to prevent reprocessing
                redisTemplate.opsForSet().remove(SttTaskProcessor.STT_RECORDING_SET, String.valueOf(sttId));
                redisTemplate.opsForHash().delete(SttTaskProcessor.STT_LAST_CHUNK_TIMESTAMP_HASH, String.valueOf(sttId));
                return;
            }

            stt.setStatus(STT.Status.ENCODING);
            STTDto dto = STTDto.fromEntity(stt);
            cacheSttStatus(dto);

            redisTemplate.opsForSet().move(SttTaskProcessor.STT_RECORDING_SET, String.valueOf(sttId), SttTaskProcessor.STT_ENCODING_SET);
            redisTemplate.opsForHash().delete(SttTaskProcessor.STT_LAST_CHUNK_TIMESTAMP_HASH, String.valueOf(sttId));

            log.info("Moved STT {} from recording to encoding queue for recovery.", sttId);
        }
    }

    @Transactional
    public void processSingleEncodingJob(Long sttId) {
        String sttIdStr = String.valueOf(sttId);
        try {
            log.info("Starting encoding job for STT ID: {}", sttId);
            File sttFile = fileService.getSTTFile(sttId);
            fileService.encodeAudioFile(sttFile);

            // 파일 서빙 가능 여부 확인
            boolean isFileReady = isFileReadyToBeServed(sttFile);
            if (!isFileReady) {
                log.error("File for STT {} is not ready after encoding and retries.", sttId);
                throw new RuntimeException("Encoded file not available for serving.");
            }

            STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
            stt.setStatus(STT.Status.ENCODED);
            sttRepository.save(stt);

            STTDto dto = STTDto.fromEntity(stt, FileDto.fromEntity(sttFile));
            cacheSttStatus(dto);

            redisTemplate.opsForSet().remove(SttTaskProcessor.STT_ENCODING_SET, sttIdStr);
            log.info("Finished encoding for STT {}. Awaiting user action to start transcription.", sttId);
        } catch (Exception e) {
            log.error("Failed to process encoding job for STT: {}", sttId, e);
            redisTemplate.opsForSet().remove(SttTaskProcessor.STT_ENCODING_SET, sttIdStr);
        }
    }

    private boolean isFileReadyToBeServed(File sttFile) throws InterruptedException {
        String fileUrl = "http://localhost:8080" + sttFile.getPath();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        for (int i = 0; i < 10; i++) {
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    log.info("File {} is ready to be served. (Attempt: {})", fileUrl, i + 1);
                    return true;
                }
                log.warn("File {} not ready yet. Status: {}. Retrying... (Attempt: {})", fileUrl, response.statusCode(), i + 1);
            } catch (IOException e) {
                log.warn("HEAD request for {} failed. Retrying... (Attempt: {})", fileUrl, i + 1, e);
            }
            Thread.sleep(300); // 300ms 대기
        }
        return false;
    }
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