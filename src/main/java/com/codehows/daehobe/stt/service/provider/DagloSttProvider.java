package com.codehows.daehobe.stt.service.provider;

import com.codehows.daehobe.stt.dto.STTResponseDto;
import com.codehows.daehobe.stt.dto.SummaryResponseDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service("dagloSttProvider")
@RequiredArgsConstructor
public class DagloSttProvider implements SttProvider {

    @Qualifier("dagloWebClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<String> requestTranscription(Resource audioFile) {
        return webClient.post()
                .uri("/stt/v1/async/transcripts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", audioFile)
                        .with("sttConfig", toJsonSttConfig())
                )
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> switch (clientResponse.statusCode().value()) {
                    case 400 -> Mono.error(new IllegalArgumentException("잘못된 요청입니다."));
                    case 401 -> Mono.error(new RuntimeException("인증 실패"));
                    case 403 -> Mono.error(new RuntimeException("권한 없음"));
                    case 413 -> Mono.error(new RuntimeException("파일이 너무 큽니다."));
                    case 415 -> Mono.error(new RuntimeException("지원되지 않는 파일 형식입니다."));
                    case 429 -> Mono.error(new RuntimeException("요청이 너무 많습니다."));
                    default -> Mono.error(new RuntimeException("클라이언트 오류"));
                })
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> Mono.error(new RuntimeException("STT 서버 내부 오류")))
                .bodyToMono(STTResponseDto.class)
                .map(response -> {
                    if (response == null || response.getRid() == null) {
                        throw new RuntimeException("rid 발급 실패");
                    }
                    return response.getRid();
                })
                .doOnError(e -> log.error("Daglo STT 요청 실패", e));
    }

    @Override
    public Mono<SttTranscriptionResult> checkTranscriptionStatus(String jobId) {
        return webClient.get()
                .uri("/stt/v1/async/transcripts/{rid}", jobId)
                .retrieve()
                .bodyToMono(STTResponseDto.class)
                .map(SttTranscriptionResult::from)
                .doOnError(e -> log.error("STT 상태 조회 실패. rid: {}", jobId, e))
                .switchIfEmpty(Mono.error(new RuntimeException("STT 상태 조회 결과가 없습니다. rid: " + jobId)));
    }

    @Override
    public Mono<String> requestSummary(String text) {
        return webClient.post()
                .uri("/nlp/v1/async/minutes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(SummaryResponseDto.class)
                .map(response -> {
                    if (response == null || response.getRid() == null) {
                        throw new RuntimeException("Summary rid 발급 실패");
                    }
                    return response.getRid();
                })
                .doOnError(e -> log.error("Daglo Summary 요청 실패", e));
    }

    @Override
    public Mono<SttSummaryResult> checkSummaryStatus(String jobId) {
        return webClient.get()
                .uri("/nlp/v1/async/minutes/{rid}", jobId)
                .retrieve()
                .bodyToMono(SummaryResponseDto.class)
                .map(SttSummaryResult::from)
                .doOnError(e -> log.error("Summary 상태 조회 실패. rid: {}", jobId, e))
                .switchIfEmpty(Mono.error(new RuntimeException("Summary 상태 조회 결과가 없습니다. rid: " + jobId)));
    }

    private String toJsonSttConfig() {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "speakerDiarization", Map.of(
                                    "enable", true
                            )
                    )
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("sttConfig JSON 생성 실패", e);
        }
    }
}
