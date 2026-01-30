package com.codehows.daehobe.stt.service.provider;

import com.codehows.daehobe.stt.dto.STTResponseDto;
import com.codehows.daehobe.stt.dto.SummaryResponseDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service("dagloSttProvider")
public class DagloSttProvider implements SttProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public DagloSttProvider(
            RestClient dagloRestClient,
            ObjectMapper objectMapper,
            @Qualifier("dagloApiCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.restClient = dagloRestClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String requestTranscription(Resource audioFile) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                STTResponseDto response = restClient.post()
                        .uri("/stt/v1/async/transcripts")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(buildMultipartBody(audioFile))
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            throw switch (res.getStatusCode().value()) {
                                case 400 -> new IllegalArgumentException("잘못된 요청입니다.");
                                case 401 -> new RuntimeException("인증 실패");
                                case 403 -> new RuntimeException("권한 없음");
                                case 413 -> new RuntimeException("파일이 너무 큽니다.");
                                case 415 -> new RuntimeException("지원되지 않는 파일 형식입니다.");
                                case 429 -> new RuntimeException("요청이 너무 많습니다.");
                                default -> new RuntimeException("클라이언트 오류");
                            };
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                            throw new RuntimeException("STT 서버 내부 오류");
                        })
                        .body(STTResponseDto.class);

                if (response == null || response.getRid() == null) {
                    throw new RuntimeException("rid 발급 실패");
                }
                return response.getRid();
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is open. Daglo API unavailable.");
            throw new RuntimeException("Daglo API temporarily unavailable", e);
        } catch (Exception e) {
            log.error("Daglo STT 요청 실패", e);
            throw e;
        }
    }

    @Override
    public SttTranscriptionResult checkTranscriptionStatus(String jobId) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                STTResponseDto response = restClient.get()
                        .uri("/stt/v1/async/transcripts/{rid}", jobId)
                        .retrieve()
                        .body(STTResponseDto.class);

                if (response == null) {
                    throw new RuntimeException("STT 상태 조회 결과가 없습니다. rid: " + jobId);
                }
                return SttTranscriptionResult.from(response);
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is open for checkTranscriptionStatus. Returning stillProcessing for rid: {}", jobId);
            return SttTranscriptionResult.stillProcessing();
        } catch (Exception e) {
            log.error("STT 상태 조회 실패. rid: {}", jobId, e);
            return SttTranscriptionResult.stillProcessing();
        }
    }

    @Override
    public String requestSummary(String text) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                SummaryResponseDto response = restClient.post()
                        .uri("/nlp/v1/async/minutes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("text", text))
                        .retrieve()
                        .body(SummaryResponseDto.class);

                if (response == null || response.getRid() == null) {
                    throw new RuntimeException("Summary rid 발급 실패");
                }
                return response.getRid();
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is open. Daglo Summary API unavailable.");
            throw new RuntimeException("Daglo Summary API temporarily unavailable", e);
        } catch (Exception e) {
            log.error("Daglo Summary 요청 실패", e);
            throw e;
        }
    }

    @Override
    public SttSummaryResult checkSummaryStatus(String jobId) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                SummaryResponseDto response = restClient.get()
                        .uri("/nlp/v1/async/minutes/{rid}", jobId)
                        .retrieve()
                        .body(SummaryResponseDto.class);

                if (response == null) {
                    throw new RuntimeException("Summary 상태 조회 결과가 없습니다. rid: " + jobId);
                }
                return SttSummaryResult.from(response);
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is open for checkSummaryStatus. Returning stillProcessing for rid: {}", jobId);
            return SttSummaryResult.stillProcessing();
        } catch (Exception e) {
            log.error("Summary 상태 조회 실패. rid: {}", jobId, e);
            return SttSummaryResult.stillProcessing();
        }
    }

    private MultiValueMap<String, HttpEntity<?>> buildMultipartBody(Resource audioFile) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", audioFile);
        builder.part("sttConfig", toJsonSttConfig());
        return builder.build();
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
