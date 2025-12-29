package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.config.webpush.RedisConfig;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.utils.DataSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.codehows.daehobe.config.webpush.RedisConfig.STT_TASK_CHANNEL;
import static com.codehows.daehobe.service.stt.SttTaskProcessor.STT_PROCESSING_SET;
import static com.codehows.daehobe.service.stt.SttTaskProcessor.STT_STATUS_HASH_PREFIX;

@Service
@RequiredArgsConstructor
public class STTService {
    private final MeetingRepository meetingRepository;
    private final STTRepository sttRepository;
    private final FileService fileService;
    private final DagloService dagloService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;


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
        STTDto cachedStatus = DataSerializer.deserialize(redisTemplate.opsForValue().get(hashKey), STTDto.class);
        if (cachedStatus != null) {
            return cachedStatus;
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
        if (!sttRepository.existsById(id)) {
            throw new RuntimeException("STT가 존재하지 않습니다.");
        }
        sttRepository.deleteById(id);
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
        return STTDto.fromEntity(newSTT, FileDto.fromEntity(newFile));
    }

    @Transactional
    public STTDto appendChunk(Long sttId, MultipartFile chunk) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        File file = fileService.appendChunk(stt.getId(), chunk, TargetType.STT);
        stt.countChunk();
        return STTDto.fromEntity(stt, FileDto.fromEntity(file));
    }

    @Transactional
    public STTDto uploadSTT(Long id, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(IllegalArgumentException::new);
        STTResponseDto response = dagloService.callDagloForSTT(file.getResource());
        STT savedStt = sttRepository.save(STT.builder()
                .rid(response.getRid())
                .meeting(meeting)
                .summary("")
                .content("")
                .status(STT.Status.PROCESSING)
                .chunkingCnt(0)
                .build());
        File savedFile = fileService.uploadFiles(savedStt.getId(), List.of(file), TargetType.STT).getFirst();
        
        // Cache status and signal processor
        cacheAndSignal(savedStt, savedFile);

        return STTDto.fromEntity(savedStt, FileDto.fromEntity(savedFile));
    }

    @Transactional
    public void requestSummary(Long sttId) {
        STT stt = sttRepository.findById(sttId).orElseThrow(EntityNotFoundException::new);
        SummaryResponseDto response = dagloService.callDagloForSummary(stt.getContent());
        stt.setSummaryRid(response.getRid());
    }

    @Transactional
    public STTResponseDto checkSTTStatus(STT stt) {
        return dagloService.checkSTTStatue(stt.getRid());
    }

    @Transactional
    public SummaryResponseDto checkSummaryStatus(STT stt) {
        return dagloService.checkSummaryStatue(stt.getSummaryRid());
    }

    @Transactional
    public STTDto finishRecording(Long sttId) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        stt.setStatus(STT.Status.PROCESSING);

        File savedFile = fileService.getSTTFile(sttId);
        fileService.encodeAudioFile(savedFile);

        Path filePath = Paths.get(fileLocation, savedFile.getSavedName());
        ByteArrayResource resource = fileToByteArrayResource(filePath, savedFile.getSavedName());

        STTResponseDto sttResponse = dagloService.callDagloForSTT(resource);
        stt.setRid(sttResponse.getRid());
        sttRepository.save(stt);

        // Cache status and signal processor
        cacheAndSignal(stt, savedFile);

        return STTDto.fromEntity(stt, FileDto.fromEntity(savedFile));
    }
    
    private void cacheAndSignal(STT stt, File file) {
        STTDto dto = STTDto.fromEntity(stt, FileDto.fromEntity(file));
        redisTemplate.opsForValue().set(STT_STATUS_HASH_PREFIX + stt.getId(), DataSerializer.serialize(dto));
        redisTemplate.opsForSet().add(STT_PROCESSING_SET, String.valueOf(stt.getId()));
        redisTemplate.convertAndSend(STT_TASK_CHANNEL, "NEW_TASK");
    }

    private ByteArrayResource fileToByteArrayResource(Path filePath, String filename) {
        try {
            byte[] fileContent = Files.readAllBytes(filePath);
            return new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }
}
