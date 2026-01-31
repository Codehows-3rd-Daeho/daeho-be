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
import com.codehows.daehobe.stt.service.processing.SttEncodingTaskExecutor;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final SttEncodingTaskExecutor sttEncodingTaskExecutor;

    @Value("${file.location}")
    private String fileLocation;

    @Value("${stt.recording.heartbeat-ttl-seconds:30}")
    private long heartbeatTtl;

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
                .chunkingCnt(0)
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
            hashRedisTemplate.delete(STT_RECORDING_HEARTBEAT_PREFIX + sttId);
            sttEncodingTaskExecutor.submitEncodingTask(sttId);
        }else {
            // 마지막 청크 시각 업데이트 -> 비정상 종료 처리에 활용 (Heartbeat 갱신)
            hashRedisTemplate.opsForValue().set(
                    STT_RECORDING_HEARTBEAT_PREFIX + sttId,
                    "",
                    heartbeatTtl,
                    TimeUnit.SECONDS
            );
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
                .chunkingCnt(0)
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
}