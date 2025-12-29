package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DagloService {

    private final SttConfigService sttConfigService;
    private final WebClient webClient;

    public STTResponseDto callDagloForSTT(Resource file) {
        try {
            STTResponseDto response = webClient.post()
                    .uri("/stt/v1/async/transcripts")//요청
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData("file", file)
                            .with("sttConfig", sttConfigService.toJson())
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> switch (clientResponse.statusCode().value()) {
                        case 400 -> Mono.error(new IllegalArgumentException("잘못된 요청입니다."));
                        case 204 -> Mono.error(new IllegalArgumentException("반환한 결과가 없습니다."));
                        case 401 -> Mono.error(new RuntimeException("인증 실패"));
                        case 403 -> Mono.error(new RuntimeException("권한 없음"));
                        case 413 -> Mono.error(new RuntimeException("파일이 너무 큽니다."));
                        case 415 -> Mono.error(new RuntimeException("지원되지 않는 파일 형식입니다."));
                        case 429 -> Mono.error(new RuntimeException("요청이 너무 많습니다."));
                        default -> Mono.error(new RuntimeException("클라이언트 오류"));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> Mono.error(new RuntimeException("STT 서버 내부 오류")))
                    .bodyToMono(STTResponseDto.class)
                    .block();
            if (response == null || response.getRid() == null) {
                throw new RuntimeException("rid 발급 실패");
            }
            return response;
        } catch (Exception e) {
            throw new RuntimeException("STT 처리 중 오류 발생", e);
        }
    }

    public SummaryResponseDto callDagloForSummary(String content) {
        try {
            SummaryResponseDto response = webClient.post()
                    .uri("/nlp/v1/async/minutes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", content))
                    .retrieve()
                    .bodyToMono(SummaryResponseDto.class)
                    .block();

            if (response == null || response.getRid() == null) {
                throw new RuntimeException("Summary rid 발급 실패");
            }
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Summary 요청 중 오류 발생", e);
        }
    }

    public STTResponseDto checkSTTStatue(String rid) {
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
    public SummaryResponseDto checkSummaryStatue(String rid) {
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

}
