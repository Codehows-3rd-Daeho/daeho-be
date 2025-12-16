package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.entity.file.STT;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.meeting.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class STTService {


    private final WebClient webClient;
    private final FileService fileService;
    private final MeetingService meetingService;
    private final STTRepository sttRepository;
    private final SttConfigService sttConfigService;



    // 저장
    public List<STTDto> uploadSTT(Long id, List<MultipartFile> files){
        Meeting meeting = meetingService.getMeetingById(id);

        List<STTDto> savedSTTs = files.stream() .map(file -> {
            //1. stt api 호출
            STTResponseDto response = callDaglo(file);

            //2. dto로 받은 반환값을 stt 엔티티에 저장
            STT stt = response.toEntity(meeting);
            STT saved = sttRepository.save(stt);

            //3.  반환
            return STTDto.fromEntity(saved);
            })
            .toList();

        //3. stt id로 fileService => 음성 파일 저장
        fileService.uploadFiles(id, files, TargetType.STT);//targetId사용해야함

        return savedSTTs;
    }

    //1-2. 응답 상태 확인
    private STTResponseDto callDaglo(MultipartFile file) {

        try {
            STTResponseDto response = webClient.post()
                .uri("/stt/v1/async/transcripts")//요청
                .contentType(MediaType.MULTIPART_FORM_DATA)
                //MultipartFile → 바이트 배열
                .body(BodyInserters.fromMultipartData("file", file.getResource())
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


    //조회
    public List<STTDto> getSTTById(Long meetingId) {

        Meeting meeting = meetingService.getMeetingById(meetingId);

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
}
