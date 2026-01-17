package com.codehows.daehobe.stt.service.processor;

import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.service.STTService;
import com.codehows.daehobe.stt.service.constant.SttTaskType;
import com.codehows.daehobe.common.utils.DataSerializer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.common.constant.KafkaConstants.STT_SUMMARIZING_TOPIC;
import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttJobProcessor {

    private final STTRepository sttRepository;
    private final STTService sttService;
    private final FileService fileService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private static final long ABNORMAL_TERMINATION_TIMEOUT_MS = 30000; // 30초
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    @Transactional
    public Mono<Void> handleAbnormalTermination(Long sttId) {
        return Mono.fromRunnable(() -> {
                    Object lastChunkTimestampObj = redisTemplate.opsForHash().get(STT_RECORDING_SESSION, String.valueOf(sttId));
                    if (lastChunkTimestampObj == null) {
                        return;
                    }

                    long lastChunkTimestamp = Long.parseLong((String) lastChunkTimestampObj);
                    if (System.currentTimeMillis() - lastChunkTimestamp > ABNORMAL_TERMINATION_TIMEOUT_MS) {
                        log.warn("Abnormal termination detected for STT ID: {}. Starting recovery process.", sttId);

                        STT stt = sttRepository.findById(sttId).orElse(null);
                        if (stt == null) {
                            log.error("STT entity not found for abnormally terminated session: {}", sttId);
                            redisTemplate.opsForSet().remove(STT_RECORDING_SET, String.valueOf(sttId));
                            redisTemplate.opsForHash().delete(STT_RECORDING_SESSION, String.valueOf(sttId));
                            return;
                        }

                        stt.setStatus(STT.Status.ENCODING);
                        STTDto dto = STTDto.fromEntity(stt);
                        cacheSttStatus(dto);

                        redisTemplate.opsForSet().move(STT_RECORDING_SET, String.valueOf(sttId), STT_ENCODING_SET);
                        redisTemplate.opsForHash().delete(STT_RECORDING_SESSION, String.valueOf(sttId));

                        log.info("Moved STT {} from recording to encoding queue for recovery.", sttId);
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Transactional
    public Mono<Void> processSingleEncodingJob(Long sttId) {
        String sttIdStr = String.valueOf(sttId);

        return Mono.fromCallable(() -> {
                    try {
                        log.info("Starting encoding job for STT ID: {}", sttId);
                        File originalFile = fileService.getSTTFile(sttId);
                        File encodedFile = fileService.encodeAudioFile(originalFile);

                        // 파일 서빙 가능 여부 확인
                        boolean isFileReady = isFileReadyToBeServed(encodedFile);
                        if (!isFileReady) {
                            log.error("File for STT {} is not ready after encoding and retries.", sttId);
                            throw new RuntimeException("Encoded file not available for serving.");
                        }

                        STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                        stt.setStatus(STT.Status.ENCODED);

                        STTDto dto = STTDto.fromEntity(stt, FileDto.fromEntity(encodedFile));
                        cacheSttStatus(dto);

                        redisTemplate.opsForSet().remove(STT_ENCODING_SET, sttIdStr);
                        log.info("Finished encoding for STT {}. Awaiting user action to start transcription.", sttId);
                        return null;
                    } catch (Exception e) {
                        log.error("Failed to process encoding job for STT: {}", sttId, e);
                        redisTemplate.opsForSet().remove(STT_ENCODING_SET, sttIdStr);
                        throw e;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(e -> {
                    log.error("Error in encoding job for STT {}", sttId, e);
                    return Mono.empty();
                });
    }

    @Transactional
    public Mono<Object> processSingleSttJob(Long sttId) {
        String hashKey = STT_STATUS_HASH_PREFIX + sttId;
        String cachedStatusStr = redisTemplate.opsForValue().get(hashKey);

        if (cachedStatusStr == null) {
            log.warn("STT job {} not found. Removing from queue.", sttId);
            redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
            return Mono.empty();
        }

        STTDto cachedStatus = Objects.requireNonNull(
                DataSerializer.deserialize(
                        cachedStatusStr,
                        STTDto.class
                )
        );

        if(cachedStatus.getStatus() != STT.Status.PROCESSING || cachedStatus.getRid() == null) {
            log.warn("STT job {} is not in PROCESSING state. Removing from queue.", sttId);
            redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
            return Mono.empty();
        }

        return sttService.checkSTTStatus(cachedStatus.getRid())
                .flatMap(result -> {
                    if (result == null) {
                        log.error("STT status check for rid {} returned null", cachedStatus.getRid());
                        return Mono.empty();
                    }
                    cachedStatus.updateContent(result.getContent());
                    cachedStatus.updateProgress(result.getProgress());
                    cacheSttStatus(cachedStatus);

                    if (result.isCompleted()) {
                        log.info("STT {} completed, transitioning to SUMMARIZING", sttId);
                        cachedStatus.updateStatus(STT.Status.SUMMARIZING);

                        return sttService.requestSummary(result.getContent())
                                .flatMap(summaryRid -> Mono.fromRunnable(() -> {
                                    cachedStatus.updateSummaryRid(summaryRid);
                                    cacheSttStatus(cachedStatus);

                                    redisTemplate.execute((RedisCallback<Object>) connection -> {
                                        connection.sRem(STT_PROCESSING_SET.getBytes(), String.valueOf(sttId).getBytes());
                                        connection.sAdd(STT_SUMMARIZING_SET.getBytes(), String.valueOf(sttId).getBytes());
                                        return null;
                                    });
                                    kafkaTemplate.send(STT_SUMMARIZING_TOPIC, String.valueOf(sttId), "");
                                    STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                                    stt.updateFromDto(cachedStatus);
                                    sttRepository.save(stt);
                                }).subscribeOn(Schedulers.boundedElastic()));
                    }
                    return Mono.empty();
                })
                .doOnError(e -> {
                    log.error("Failed to process STT job for sttId: {}", sttId, e);
                    if (isUnrecoverableError((Exception) e)) {
                        redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
                    }
                });
    }

    @Transactional
    public Mono<Object> processSingleSummaryJob(Long sttId) {
        String hashKey = STT_STATUS_HASH_PREFIX + sttId;
        String cachedStatusStr = redisTemplate.opsForValue().get(hashKey);

        if (cachedStatusStr == null) {
            log.warn("STT summary job {} not found. Removing from queue.", sttId);
            redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
            return Mono.empty();
        }

        STTDto cachedStatus = Objects.requireNonNull(
                DataSerializer.deserialize(
                        cachedStatusStr,
                        STTDto.class
                )
        );

        if(cachedStatus.getStatus() != STT.Status.SUMMARIZING || cachedStatus.getSummaryRid() == null) {
            log.warn("STT summary job {} is not in SUMMARIZING state", sttId);
            redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
            return Mono.empty();
        }

        return sttService.checkSummaryStatus(cachedStatus.getSummaryRid())
                .flatMap(result -> {
                    if (result == null) {
                        log.error("Summary status check for rid {} returned null", cachedStatus.getSummaryRid());
                        return Mono.empty();
                    }
                    cachedStatus.updateSummary(result.getSummaryText());
                    cachedStatus.updateProgress(result.getProgress());
                    cacheSttStatus(cachedStatus);

                    if (result.isCompleted()) {
                        log.info("Summary for sttId {} completed", sttId);
                        cachedStatus.updateStatus(STT.Status.COMPLETED);
                        cacheSttStatus(cachedStatus);

                        return Mono.fromRunnable(() -> {
                            redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                            redisTemplate.expire(STT_STATUS_HASH_PREFIX + sttId, 5, TimeUnit.MINUTES);

                            STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
                            stt.updateFromDto(cachedStatus);
                            sttRepository.save(stt);
                        }).subscribeOn(Schedulers.boundedElastic());
                    }
                    return Mono.empty();
                })
                .doOnError(e -> {
                    log.error("Failed to process summary job for sttId: {}", sttId, e);
                    if (isUnrecoverableError((Exception) e)) {
                        redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                    }
                });
    }

    public void cacheSttStatus(STTDto stt) {
        try {
            redisTemplate.opsForValue().set(
                    STT_STATUS_HASH_PREFIX + stt.getId(),
                    Objects.requireNonNull(DataSerializer.serialize(stt)),
                    30,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("Failed to cache STT status: {}", stt.getId(), e);
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
            Thread.sleep(300); // 300ms 대기
        }
        return false;
    }

    private boolean isUnrecoverableError(Exception e) {
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e.getCause() instanceof NumberFormatException;
    }
}