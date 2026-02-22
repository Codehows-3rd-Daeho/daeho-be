package com.codehows.daehobe.stt.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX;
import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class STTService {
    private final MeetingRepository meetingRepository;
    private final STTRepository sttRepository;
    private final FileService fileService;
    @Qualifier("dagloSttProvider")
    private final SttProvider sttProvider;
    private final StringRedisTemplate hashRedisTemplate;
    private final SttCacheService sttCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    @Value("${file.location}")
    private String fileLocation;

    @Value("${stt.recording.heartbeat-ttl-seconds:30}")
    private long heartbeatTtl;

    // 단일 인스턴스용 in-memory 락: 동일 sttId에 대한 중복 복구 방지
    private final ConcurrentHashMap<Long, Boolean> recoveryInProgress = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public STTDto getSTTById(Long id) {
        STT stt = sttRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        File audioFile = fileService.getSTTFile(id);
        return STTDto.fromEntity(stt, FileDto.fromEntity(audioFile));
    }

    @Transactional(readOnly = true)
    public STTDto getDynamicSttStatus(Long sttId) {
        STTDto cachedDto = sttCacheService.getCachedSttStatus(sttId);
        if (cachedDto != null) {
            return cachedDto;
        }
        return getSTTById(sttId);
    }

    @Transactional(readOnly = true)
    public List<STTDto> getSTTsByMeetingId(Long meetingId, Long memberId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(IllegalArgumentException::new);

        if (meeting == null) {
            return List.of();
        }

        List<STT> stts = sttRepository.findByMeetingIdWithStatusCondition(meetingId, memberId);
        List<Long> sttIds = stts.stream().map(STT::getId).toList();
        List<File> files = fileService.getSTTFiles(sttIds);
        Map<Long, File> fileByTargetId = files.stream().collect(Collectors.toMap(File::getTargetId, file -> file));

        return stts.stream()
                .map(stt -> {
                    File file = fileByTargetId.get(stt.getId());
                    if(file == null) return STTDto.fromEntity(stt);
                    return STTDto.fromEntity(stt, FileDto.fromEntity(file));
                })
                .toList();
    }

    @Transactional
    public void updateSummary(Long id, String content) {
        STT stt = sttRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + id));
        stt.updateSummary(content);
    }

    @Transactional
    public void deleteSTT(Long id) {
        STT stt = sttRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        File savedFile = fileService.getSTTFile(stt.getId());
        fileService.updateFiles(id, null, List.of(savedFile.getFileId()), TargetType.STT);
        sttRepository.delete(stt);
        hashRedisTemplate.delete(STT_STATUS_HASH_PREFIX + id);
        hashRedisTemplate.delete(STT_RECORDING_HEARTBEAT_PREFIX + id);
    }

    @Transactional
    public STTDto startRecording(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid meeting ID: " + meetingId));
        STT newSTT = sttRepository.save(STT.builder()
                .meeting(meeting)
                .summary("")
                .content("")
                .status(STT.Status.RECORDING)
                .build());

        String savedFileName = "stt-recording-" + UUID.randomUUID() + ".wav";
        File newFile = fileService.createFile(savedFileName, newSTT.getId(), TargetType.STT);

        STTDto sttDto = STTDto.fromEntity(newSTT, FileDto.fromEntity(newFile));
        sttCacheService.cacheSttStatus(sttDto);
        messagingTemplate.convertAndSend("/topic/stt/updates/" + sttDto.getMeetingId(), sttDto);
        // 비정상 종료 감지를 위한 Heartbeat 키 생성
        hashRedisTemplate.opsForValue().set(STT_RECORDING_HEARTBEAT_PREFIX + newSTT.getId(), "", heartbeatTtl, TimeUnit.SECONDS);
        return sttDto;
    }

    @Transactional
    public STTDto appendChunk(Long sttId, MultipartFile chunk, Boolean finish) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        File file = fileService.appendChunk(stt.getId(), chunk, TargetType.STT);

        STTDto sttDto = STTDto.fromEntity(stt, FileDto.fromEntity(file));

        if (Boolean.TRUE.equals(finish)) {
            stt.setStatus(STT.Status.ENCODING);
            sttDto.updateStatus(STT.Status.ENCODING);
            sttCacheService.cacheSttStatus(sttDto);
            messagingTemplate.convertAndSend("/topic/stt/updates/" + sttDto.getMeetingId(), sttDto);
            try {
                hashRedisTemplate.delete(STT_RECORDING_HEARTBEAT_PREFIX + sttId);
            } catch (Exception e) {
                log.warn("[Heartbeat] Failed to delete heartbeat for sttId={}. Redis may be unavailable.", sttId, e);
            }
            processSingleEncodingJob(sttId);
        } else {
            // 마지막 청크 시각 업데이트 -> 비정상 종료 처리에 활용 (Heartbeat 갱신)
            try {
                hashRedisTemplate.opsForValue().set(
                        STT_RECORDING_HEARTBEAT_PREFIX + sttId,
                        "",
                        heartbeatTtl,
                        TimeUnit.SECONDS
                );
            } catch (Exception e) {
                log.warn("[Heartbeat] Failed to renew heartbeat for sttId={}. Redis may be unavailable.", sttId, e);
            }
        }

        return sttDto;
    }

    @Transactional
    public STTDto uploadAndTranslate(Long id, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(IllegalArgumentException::new);
        String rid = sttProvider.requestTranscription(file.getResource());
        // 최초 생성은 ENCODED 상태로 DB 저장 (PROCESSING은 Redis-only)
        STT savedStt = sttRepository.save(STT.builder()
                .rid(rid)
                .meeting(meeting)
                .summary("")
                .content("")
                .status(STT.Status.ENCODED)
                .build());
        File savedFile = fileService.uploadFiles(savedStt.getId(), List.of(file), TargetType.STT).getFirst();
        STTDto sttDto = STTDto.fromEntity(savedStt, FileDto.fromEntity(savedFile));
        // Redis 캐시는 PROCESSING 상태로
        sttDto.updateStatus(STT.Status.PROCESSING);
        sttDto.updateRetryCount(0);
        sttCacheService.cacheSttStatus(sttDto);
        sttCacheService.addToPollingSet(savedStt.getId(), STT.Status.PROCESSING);
        messagingTemplate.convertAndSend("/topic/stt/updates/" + sttDto.getMeetingId(), sttDto);
        return sttDto;
    }

    @Transactional(readOnly = true)
    public STTDto startTranslateForRecorded(Long sttId) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));

        File savedFile = fileService.getSTTFile(sttId);
        Path filePath = Paths.get(fileLocation, savedFile.getSavedName());

        Resource resource = new FileSystemResource(filePath);
        String rid = sttProvider.requestTranscription(resource);

        // Redis-only: DB 저장 제거, Redis 캐시 + polling set만 사용
        STTDto sttDto = STTDto.fromEntity(stt, FileDto.fromEntity(savedFile));
        sttDto.updateStatus(STT.Status.PROCESSING);
        sttDto.updateRid(rid);
        sttDto.updateRetryCount(0);
        sttCacheService.cacheSttStatus(sttDto);
        sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);
        messagingTemplate.convertAndSend("/topic/stt/updates/" + sttDto.getMeetingId(), sttDto);
        return sttDto;
    }

    public void handleAbnormalTermination(Long sttId) {
        if (recoveryInProgress.putIfAbsent(sttId, Boolean.TRUE) != null) {
            log.info("Recovery already in progress for STT {}. Skipping duplicate.", sttId);
            return;
        }
        try {
            log.warn("Abnormal termination detected for STT ID: {}", sttId);
            STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
            if (cachedStatus == null || cachedStatus.getStatus() != STT.Status.RECORDING) {
                log.warn("STT {} is not in RECORDING state. Skipping recovery.", sttId);
                return;
            }
            processSingleEncodingJob(sttId);
            log.info("Completed abnormal termination recovery for STT {}.", sttId);
        } finally {
            recoveryInProgress.remove(sttId);
        }
    }

    // Redis 재시작 후 캐시 없는 고아 복구 (2차 안전망)
    // createdAt 기준 임계 시간 초과 시 DB로 캐시 재구성 후 handleAbnormalTermination 위임
    @Transactional
    public void handleAbnormalTerminationIfStuck(Long sttId, long thresholdHours) {
        STT stt = sttRepository.findById(sttId).orElse(null);
        if (stt == null || stt.getStatus() != STT.Status.RECORDING) return;

        LocalDateTime threshold = LocalDateTime.now().minusHours(thresholdHours);
        if (stt.getCreatedAt().isAfter(threshold)) {
            log.info("[SafetyNet-DB] sttId={} RECORDING but within threshold. Skipping.", sttId);
            return;
        }

        log.warn("[SafetyNet-DB] sttId={} stuck since {}. Rebuilding cache.", sttId, stt.getCreatedAt());
        STTDto reconstructed = STTDto.fromEntity(stt);
        sttCacheService.cacheSttStatus(reconstructed);

        handleAbnormalTermination(sttId);
    }

    private void processSingleEncodingJob(Long sttId) {
        try {
            STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);

            cachedStatus.updateStatus(STT.Status.ENCODING);
            sttCacheService.cacheSttStatus(cachedStatus);
            messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            log.info("Starting encoding job for STT ID: {}", sttId);
            File originalFile = fileService.getSTTFile(sttId);
            File encodedFile = fileService.encodeAudioFile(originalFile);

            cachedStatus.updateFile(FileDto.fromEntity(encodedFile));
            cachedStatus.updateStatus(STT.Status.ENCODED);
            sttCacheService.cacheSttStatus(cachedStatus);
            messagingTemplate.convertAndSend("/topic/stt/updates/" + cachedStatus.getMeetingId(), cachedStatus);

            // ENCODED 상태에서 DB 저장 (사용자 복귀 대비)
            STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
            stt.setStatus(STT.Status.ENCODED);
            sttRepository.save(stt);

            log.info("Finished encoding for STT {}. Awaiting user action to start transcription.", sttId);
        } catch (Exception e) {
            log.error("Failed to process encoding job for STT: {}", sttId, e);
            throw new RuntimeException(e); // Re-throw the exception to be handled by the consumer
        }
    }

//    private boolean isFileReadyToBeServed(File sttFile) throws InterruptedException {
//        String fileUrl = appBaseUrl + sttFile.getPath();
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(fileUrl))
//                .method("HEAD", HttpRequest.BodyPublishers.noBody())
//                .build();
//
//        for (int i = 0; i < 10; i++) {
//            try {
//                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
//                if (response.statusCode() == 200) {
//                    log.info("File {} is ready to be served. (Attempt: {})", fileUrl, i + 1);
//                    return true;
//                }
//                log.warn("File {} not ready yet. Status: {}. Retrying... (Attempt: {})", fileUrl, response.statusCode(), i + 1);
//            } catch (IOException e) {
//                log.warn("HEAD request for {} failed. Retrying... (Attempt: {})", fileUrl, i + 1, e);
//            }
//            Thread.sleep(300);
//        }
//        return false;
//    }
}