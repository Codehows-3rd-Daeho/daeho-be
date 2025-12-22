package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class STTService {


    private final WebClient webClient;
    private final FileService fileService;
    private final MeetingRepository meetingRepository;
    private final STTRepository sttRepository;
    private final SttConfigService sttConfigService;

    @Value("${file.location}")
    private String fileLocation;


    // 저장
    public List<STTDto> uploadSTT(Long id, List<MultipartFile> files){
        Meeting meeting = meetingRepository.findById(id).orElseThrow(IllegalArgumentException::new);

        List<STTDto> savedSTTs = files.stream().map(file -> {
            //1. stt api 호출
            STTResponseDto response = callDaglo(file.getResource());

            //2. dto로 받은 반환값을 stt 엔티티에 저장
            STT stt = response.toEntity(meeting);
            STT saved = sttRepository.save(stt);

            //3.  반환
            return STTDto.fromEntity(saved);
        }).toList();

        //3. stt id로 fileService => 음성 파일 저장
        fileService.uploadFiles(id, files, TargetType.STT);//targetId사용해야함

        return savedSTTs;
    }

    //1-1. api 호출
    private STTResponseDto callDaglo(Resource file) {
        try {
            STTResponseDto response = webClient.post()
                .uri("/stt/v1/async/transcripts")//요청
                .contentType(MediaType.MULTIPART_FORM_DATA)
                //MultipartFile → 바이트 배열
                .body(BodyInserters.fromMultipartData("file", file)
                        .with("sttConfig", sttConfigService.toJson())
                )
                .retrieve()//응답 받기
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    switch (clientResponse.statusCode().value()) {
                        case 400: return Mono.error(new IllegalArgumentException("잘못된 요청입니다."));
                        case 204: return Mono.error(new IllegalArgumentException("반환한 결과가 없습니다."));
                        case 401: return Mono.error(new RuntimeException("인증 실패"));
                        case 403: return Mono.error(new RuntimeException("권한 없음"));
                        case 413: return Mono.error(new RuntimeException("파일이 너무 큽니다."));
                        case 415: return Mono.error(new RuntimeException("지원되지 않는 파일 형식입니다."));
                        case 429: return Mono.error(new RuntimeException("요청이 너무 많습니다."));
                        default: return Mono.error(new RuntimeException("클라이언트 오류"));
                    }
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse ->
                        Mono.error(new RuntimeException("STT 서버 내부 오류"))
                )
                .bodyToMono(STTResponseDto.class)// Dto(String)로 변환
                .block();//비동기 → 동기로 변경

            if (response == null || response.getRid() == null) {
                throw new RuntimeException("rid 발급 실패");
            }

            //상태 확인
            //rid 추출
            String rid = response.getRid();

            int maxRetries = 100;//반복 횟수
            int intervalMs = 2000;//시도 사이 간격 ms

            //Polling: 변환 완료될 때까지 반복 확인
            for (int i = 0; i < maxRetries; i++) {
                STTResponseDto result = checkSTTStatus(rid);
                System.out.println("Attempt " + i + " - status: " + result.getStatus());
                if (result.isCompleted()) {
                    return result; // 변환 완료
                }
                Thread.sleep(intervalMs);
            }


            STTResponseDto lastResult = checkSTTStatus(rid); // 마지막 상태 확인(콘솔용)
            throw new RuntimeException("STT 변환 완료 대기 시간 초과. rid: " + rid + ", last status: " + lastResult.getStatus());
        } catch (Exception e) {
            throw new RuntimeException("STT 처리 중 오류 발생", e);
        }
    }


    //1-2. rid로 stt 상태 확인
    private STTResponseDto checkSTTStatus(String rid) {
        System.out.println("==========================================");
        System.out.println("checkSTTStatus 실행 확인");
        System.out.println("==========================================");


        STTResponseDto result = webClient.get()
                .uri("/stt/v1/async/transcripts/{rid}", rid)
                .retrieve()
                .bodyToMono(STTResponseDto.class)
                .block();

        if (result == null) {
            throw new RuntimeException("STT 상태 조회 실패. rid: " + rid);
        }
        return result;
    }


    //STT 조회
    public List<STTDto> getSTTById(Long meetingId) {

        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(IllegalArgumentException::new);

        //1. meeting id로 존재 확인
        if ( meeting == null) {
            System.out.println("해당 회의가 존재하지 않습니다.");
            return List.of(); // 빈 리스트 반환
        };

        List<STT> stts = sttRepository.findByMeetingId(meetingId);

        // 엔티티 -> DTO
        return stts.stream()
                .map(STTDto::fromEntity)
                .toList();
    }
//==================================================요약==================================================================
    //요약
    @Transactional
    public SummaryResponseDto summarySTT(Long sttId, String content) {

        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("STT 없음"));

        //1. api 호출
        try
        {
            SummaryResponseDto response = webClient.post()
                    .uri("/nlp/v1/async/minutes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", content))//JSON 문자열로 변환
                    .retrieve()
                    .bodyToMono(SummaryResponseDto.class)
                    .block();

            if (response == null || response.getRid() == null) {
                throw new RuntimeException("rid 발급 실패");
            }

            //상태 확인
            //rid 추출
            String rid = response.getRid();

            int maxRetries = 100;//반복 횟수
            int intervalMs = 2000;//시도 사이 간격 ms

            //Polling: 변환 완료될 때까지 반복 확인
            for (int i = 0; i < maxRetries; i++) {
                SummaryResponseDto result = checkSummaryStatus(rid);
                System.out.println("Attempt " + i + " - status: " + result.getStatus());
                if (result.isCompleted()) {
                    stt.updateSummary(result.getSummaryText());
                    return result; // 변환 완료
                }
                Thread.sleep(intervalMs);
            }
            SummaryResponseDto lastResult = checkSummaryStatus(rid); // 마지막 상태 확인(콘솔용)
            throw new RuntimeException("STT 변환 완료 대기 시간 초과. rid: " + rid + ", last status: " + lastResult.getStatus());

        } catch (Exception e) {
            throw new RuntimeException("STT 처리 중 오류 발생", e);
        }


    }


    //2. rid로 stt 상태 확인
    private SummaryResponseDto checkSummaryStatus(String rid){
        System.out.println("==========================================");
        System.out.println("checkSummaryStatus 실행 확인");
        System.out.println("==========================================");

        SummaryResponseDto result = webClient.get()
                .uri("/nlp/v1/async/minutes/{rid}", rid)
                .retrieve()
                .bodyToMono(SummaryResponseDto.class)
                .block();

        if (result == null) {
            throw new RuntimeException("STT 상태 조회 실패. rid: " + rid);
        }
        return result;

    }


//==================================================삭제==================================================================

    @Transactional
    public void deleteSTT(Long id) {
        if (!sttRepository.existsById(id)) {
            throw new RuntimeException("STT가 존재하지 않습니다.");
        }
        sttRepository.deleteById(id); // 완전 삭제
    }

    public Long startRecording(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid meeting ID: " + meetingId));

        String savedFileName = UUID.randomUUID().toString();

        STT stt = STT.builder()
                .meeting(meeting)
                .content("")
                .status(STT.Status.RECORDING)
                .tempFileName(savedFileName + ".wav")
                .build();

        sttRepository.save(stt);
        return stt.getId();
    }

    public void appendChunk(Long sttId, MultipartFile chunk) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));

        File savedFileId = fileService.appendChunk(stt.getId(), stt.getTempFileName(), chunk, TargetType.STT, stt.getFileId());
        stt.setFileId(savedFileId.getFileId());
    }

    public STTDto finishRecording(Long sttId) {
        STT stt = sttRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid STT ID: " + sttId));
        stt.setStatus(STT.Status.PROCESSING);
        sttRepository.save(stt);

        File file = fileService.getFileById(stt.getFileId());
        Path filePath = Paths.get(fileLocation, file.getPath());
        Resource resource = new FileSystemResource(filePath);

        STTResponseDto response = callDaglo(resource);

        stt.setContent(response.getContent());
        summarySTT(stt.getId(), response.getContent());

        // Re-fetch to get the summary
        STT updatedStt = sttRepository.findById(sttId).orElseThrow();
        updatedStt.setStatus(STT.Status.COMPLETED);
        sttRepository.save(updatedStt);

        // Clean up the temporary file
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log this error, but don't fail the entire operation
            System.err.println("Failed to delete temporary file: " + filePath);
        }

        return STTDto.fromEntity(updatedStt);
    }
}
