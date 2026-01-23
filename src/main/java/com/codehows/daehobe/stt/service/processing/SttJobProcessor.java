package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.config.redis.RedisMessageBroker;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map; // Map import 추가
import java.util.Objects;

import static com.codehows.daehobe.common.constant.KafkaConstants.STT_ENCODING_TOPIC;
import static com.codehows.daehobe.common.constant.KafkaConstants.STT_SUMMARIZING_TOPIC;
import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttJobProcessor {

    private final STTRepository sttRepository;
    private final FileService fileService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    @Qualifier("dagloSttProvider")
    private final SttProvider sttProvider;
    private final SttCacheService sttCacheService;
    private final RedisMessageBroker redisMessageBroker;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    @Transactional
    public void handleAbnormalTermination(Long sttId) {
        log.warn("Abnormal termination detected for STT ID: {}. Starting recovery process.", sttId);

        STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
        if (cachedStatus == null || cachedStatus.getStatus() != STT.Status.RECORDING) {
            log.error("STT entity not found for abnormally terminated session: {}", sttId);
            return;
        }

        kafkaTemplate.send(STT_ENCODING_TOPIC, String.valueOf(sttId), "abnormal-termination-encoding");

        log.info("Moved STT {} from recording to encoding queue for recovery.", sttId);
    }

    @Transactional
    public void processSingleEncodingJob(Long sttId) {
        try {
            STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);

            cachedStatus.updateStatus(STT.Status.ENCODING);
            sttCacheService.cacheSttStatus(cachedStatus);
            redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            log.info("Starting encoding job for STT ID: {}", sttId);
            File originalFile = fileService.getSTTFile(sttId);
            File encodedFile = fileService.encodeAudioFile(originalFile);
            boolean isFileReady = isFileReadyToBeServed(encodedFile);
            if (!isFileReady) {
                log.error("File for STT {} is not ready after encoding and retries.", sttId);
                throw new RuntimeException("Encoded file not available for serving.");
            }

            cachedStatus.updateFile(FileDto.fromEntity(encodedFile));
            cachedStatus.updateStatus(STT.Status.ENCODED);
            sttCacheService.cacheSttStatus(cachedStatus);
            redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            log.info("Finished encoding for STT {}. Awaiting user action to start transcription.", sttId);
        } catch (Exception e) {
            log.error("Failed to process encoding job for STT: {}", sttId, e);
            throw new RuntimeException(e); // Re-throw the exception to be handled by the consumer
        }
    }

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
            SttTranscriptionResult result = sttProvider.checkTranscriptionStatus(cachedStatus.getRid()).block();

            if (result == null) {
                log.error("STT status check for rid {} returned null", cachedStatus.getRid());
                throw new RuntimeException("STT status check returned null");
            }

            cachedStatus.updateContent(result.getContent());
            cachedStatus.updateProgress(result.getProgress());
            sttCacheService.cacheSttStatus(cachedStatus);
            redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            if (result.isCompleted()) {
                log.info("STT {} completed, transitioning to SUMMARIZING", sttId);

                String summaryRid = sttProvider.requestSummary(result.getContent()).block();

                cachedStatus.updateStatus(STT.Status.SUMMARIZING);
                cachedStatus.updateSummaryRid(summaryRid);
                sttCacheService.cacheSttStatus(cachedStatus);
                redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

                kafkaTemplate.send(STT_SUMMARIZING_TOPIC, String.valueOf(sttId), "start-summarizing");
            } else {
                log.info("STT {} is still in progress ({}%). Will retry.", sttId, result.getProgress());
                throw new SttNotCompletedException("STT job not yet completed for sttId: " + sttId);
            }
        } catch (Exception e) {
            if (e instanceof SttNotCompletedException) {
                throw e;
            }
            log.error("Failed to process STT job for sttId: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                throw new RuntimeException("Unrecoverable error in STT processing for sttId: " + sttId, e);
            }
            throw new RuntimeException(e);
        }
    }

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
            SttSummaryResult result = sttProvider.checkSummaryStatus(cachedStatus.getSummaryRid()).block();

            if (result == null) {
                log.error("Summary status check for rid {} returned null", cachedStatus.getSummaryRid());
                throw new RuntimeException("Summary status check returned null");
            }

            cachedStatus.updateSummary(result.getSummaryText());
            cachedStatus.updateProgress(result.getProgress());
            sttCacheService.cacheSttStatus(cachedStatus);
            redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            if (result.isCompleted()) {
                log.info("Summary for sttId {} completed", sttId);
                cachedStatus.updateStatus(STT.Status.COMPLETED);
                sttCacheService.cacheSttStatus(cachedStatus);
                redisMessageBroker.publish("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

                STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                stt.updateFromDto(cachedStatus);
                sttRepository.save(stt);
            } else {
                log.info("STT summary {} is still in progress ({}%). Will retry.", sttId, result.getProgress());
                throw new SttNotCompletedException("STT summary job not yet completed for sttId: " + sttId);
            }
        } catch (Exception e) {
            if (e instanceof SttNotCompletedException) {
                throw e;
            }
            log.error("Failed to process summary job for sttId: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                throw new RuntimeException("Unrecoverable error in summary processing for sttId: " + sttId, e);
            }
            throw new RuntimeException(e);
        }
    }

    private boolean isFileReadyToBeServed(File sttFile) throws InterruptedException {
        String fileUrl = appBaseUrl + sttFile.getPath();
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
            Thread.sleep(300);
        }
        return false;
    }

    private boolean isUnrecoverableError(Exception e) {
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e.getCause() instanceof NumberFormatException;
    }
}
