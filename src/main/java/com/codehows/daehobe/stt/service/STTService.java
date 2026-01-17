package com.codehows.daehobe.stt.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.service.constant.SttTaskType;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import com.codehows.daehobe.common.utils.DataSerializer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.codehows.daehobe.common.constant.KafkaConstants.*;
import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class STTService {
    private final MeetingRepository meetingRepository;
    private final STTRepository sttRepository;
    private final FileService fileService;
    @Qualifier("dagloSttProvider")
    private final SttProvider sttProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${file.location}")
    private String fileLocation;

    @Transactional(readOnly = true)
    public STTDto getSTTById(Long id) {
        STT stt = sttRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        File audioFile = fileService.getSTTFile(id);
        return STTDto.fromEntity(stt, FileDto.fromEntity(audioFile));
    }

    @Transactional(readOnly = true)
    public STTDto getDynamicSttStatus(Long sttId) {
        String hashKey = STT_STATUS_HASH_PREFIX + sttId;
        String cachedStatus = redisTemplate.opsForValue().get(hashKey);
        if (cachedStatus != null) {
            // Cache Hit!
            return Objects.requireNonNull(
                    DataSerializer.deserialize(
                            cachedStatus,
                            STTDto.class
                    )
            );
        }
        return getSTTById(sttId);
    }

    @Transactional(readOnly = true)
    public List<STTDto> getSTTsByMeetingId(Long meetingId, Long memberId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(IllegalArgumentException::new);

        if (meeting == null) {
            System.out.println("해당 회의가 존재하지 않습니다.");
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

        //모든 큐에서 데이터 삭제 & 캐시 데이터 삭제
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.sRem(STT_PROCESSING_SET.getBytes(), String.valueOf(id).getBytes());
            connection.sRem(STT_SUMMARIZING_SET.getBytes(), String.valueOf(id).getBytes());
            connection.sRem(STT_ENCODING_SET.getBytes(), String.valueOf(id).getBytes());
            connection.sRem(STT_RECORDING_SET.getBytes(), String.valueOf(id).getBytes());
            connection.hDel(STT_RECORDING_SESSION.getBytes(), String.valueOf(id).getBytes());
            connection.del((STT_STATUS_HASH_PREFIX + id).getBytes());
            return null;
        });
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

        // 태스크 처리 큐 등록(RECORDING)
        redisTemplate.opsForSet().add(
                STT_RECORDING_SET,
                String.valueOf(newSTT.getId())
        );
        // 비정상 종료 감지 스케줄러 트리거
        kafkaTemplate.send(STT_ABNORMAL_TERMINATION_TOPIC, String.valueOf(newSTT.getId()), "");
        return cacheSttStatus(newSTT, newFile); // STT 상태 캐시 및 반환
    }

    @Transactional
    public STTDto appendChunk(Long sttId, MultipartFile chunk, Boolean finish) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        File file = fileService.appendChunk(stt.getId(), chunk, TargetType.STT);

        // 마지막 청크 시각 업데이트 -> 비정상 종료 처리에 활용
        redisTemplate.opsForHash().put(
                STT_RECORDING_SESSION,
                String.valueOf(sttId),
                String.valueOf(System.currentTimeMillis())
        );

        if(finish != null && finish) {
            stt.setStatus(STT.Status.ENCODING);
            // 태스크 처리 큐 변경(RECORDING -> ENCODING)
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.sRem(STT_RECORDING_SET.getBytes(), String.valueOf(sttId).getBytes());
                connection.sAdd(STT_ENCODING_SET.getBytes(), String.valueOf(sttId).getBytes());
                return null;
            });
            // 인코딩 스케줄러 트리거
            kafkaTemplate.send(STT_ENCODING_TOPIC, String.valueOf(sttId), "");
        }
        return cacheSttStatus(stt, file); // STT 상태 캐시 및 반환
    }

    @Transactional
    public STTDto uploadAndTranslate(Long id, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(IllegalArgumentException::new);
        String rid = sttProvider.requestTranscription(file.getResource()).block();
        STT savedStt = sttRepository.save(STT.builder()
                .rid(rid)
                .meeting(meeting)
                .summary("")
                .content("")
                .status(STT.Status.PROCESSING)
                .chunkingCnt(0)
                .build());
        File savedFile = fileService.uploadFiles(savedStt.getId(), List.of(file), TargetType.STT).getFirst();

        // 태스크 처리 큐 등록(STT)
        redisTemplate.opsForSet().add(
                STT_PROCESSING_SET,
                String.valueOf(savedStt.getId())
        );
        // STT 스케줄러 트리거
        kafkaTemplate.send(STT_PROCESSING_TOPIC, String.valueOf(savedStt.getId()), "");
        return cacheSttStatus(savedStt, savedFile); // STT 상태 캐시 및 반환
    }

    @Transactional
    public STTDto startTranslateForRecorded(Long sttId) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        stt.setStatus(STT.Status.PROCESSING);

        File savedFile = fileService.getSTTFile(sttId);
        Path filePath = Paths.get(fileLocation, savedFile.getSavedName());

        Resource resource = new FileSystemResource(filePath);
        String rid = sttProvider.requestTranscription(resource).block();
        stt.setRid(rid);
        sttRepository.save(stt);

        // 태스크 처리 큐 등록(STT)
        redisTemplate.opsForSet().add(
                STT_PROCESSING_SET,
                String.valueOf(stt.getId())
        );
        // STT 스케줄러 트리거
        kafkaTemplate.send(STT_PROCESSING_TOPIC, String.valueOf(stt.getId()), "");
        return cacheSttStatus(stt, savedFile); // STT 상태 캐시 및 반환
    }

    public Mono<String> requestSummary(String content) {
        return sttProvider.requestSummary(content);
    }

    public Mono<SttTranscriptionResult> checkSTTStatus(String rid) {
        return sttProvider.checkTranscriptionStatus(rid);
    }

    public Mono<SttSummaryResult> checkSummaryStatus(String rid) {
        return sttProvider.checkSummaryStatus(rid);
    }

    private STTDto cacheSttStatus(STT stt, File savedFile) {
        STTDto sttDto = STTDto.fromEntity(
                stt,
                FileDto.fromEntity(savedFile)
        );
        redisTemplate.opsForValue().set(
                STT_STATUS_HASH_PREFIX + stt.getId(),
                Objects.requireNonNull(
                        DataSerializer.serialize(sttDto)
                )
        );
        return sttDto;
    }
}
