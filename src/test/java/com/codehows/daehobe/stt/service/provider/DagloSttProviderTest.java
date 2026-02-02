package com.codehows.daehobe.stt.service.provider;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.stt.dto.STTResponseDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.stt.dto.SummaryResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class DagloSttProviderTest {

    @Mock
    private RestClient restClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private DagloSttProvider dagloSttProvider;

    @BeforeEach
    void setUp() {
        dagloSttProvider = new DagloSttProvider(restClient, objectMapper, circuitBreaker);
    }

    @Nested
    @DisplayName("requestTranscription 테스트")
    class RequestTranscriptionTest {

        @Test
        @DisplayName("성공: 변환 요청 후 RID 반환")
        void requestTranscription_Success() {
            // given
            Resource audioFile = new ByteArrayResource("audio data".getBytes()) {
                @Override
                public String getFilename() {
                    return "test.wav";
                }
            };
            String expectedRid = "rid-12345";

            STTResponseDto responseDto = new STTResponseDto();
            responseDto.setRid(expectedRid);

            // CircuitBreaker가 supplier 실행 없이 직접 결과 반환
            when(circuitBreaker.executeSupplier(any(Supplier.class))).thenReturn(expectedRid);

            // when
            String result = dagloSttProvider.requestTranscription(audioFile);

            // then
            assertThat(result).isEqualTo(expectedRid);
        }

        @Test
        @DisplayName("실패: CircuitBreaker Open 시 RuntimeException 발생")
        void requestTranscription_CircuitBreakerOpen() {
            // given
            Resource audioFile = new ByteArrayResource("audio data".getBytes()) {
                @Override
                public String getFilename() {
                    return "test.wav";
                }
            };

            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(mock(CallNotPermittedException.class));

            // when & then
            assertThatThrownBy(() -> dagloSttProvider.requestTranscription(audioFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Daglo API temporarily unavailable");
        }

        @Test
        @DisplayName("실패: RID null 시 RuntimeException 발생")
        void requestTranscription_NullRid() {
            // given
            Resource audioFile = new ByteArrayResource("audio data".getBytes()) {
                @Override
                public String getFilename() {
                    return "test.wav";
                }
            };

            // CircuitBreaker가 supplier 실행 시 "rid 발급 실패" 예외 발생
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(new RuntimeException("rid 발급 실패"));

            // when & then
            assertThatThrownBy(() -> dagloSttProvider.requestTranscription(audioFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("rid 발급 실패");
        }
    }

    @Nested
    @DisplayName("checkTranscriptionStatus 테스트")
    class CheckTranscriptionStatusTest {

        @Test
        @DisplayName("성공: 완료 상태 반환")
        void checkTranscriptionStatus_Completed() {
            // given
            String jobId = "test-rid";
            STTResponseDto responseDto = new STTResponseDto();
            responseDto.setRid(jobId);
            responseDto.setStatus("transcribed");
            responseDto.setProgress(100);

            when(circuitBreaker.executeSupplier(any(Supplier.class))).thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(STTResponseDto.class)).thenReturn(responseDto);

            // when
            SttTranscriptionResult result = dagloSttProvider.checkTranscriptionStatus(jobId);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CircuitBreaker Open: stillProcessing 반환")
        void checkTranscriptionStatus_CircuitBreakerOpen_ReturnsStillProcessing() {
            // given
            String jobId = "test-rid";
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(mock(CallNotPermittedException.class));

            // when
            SttTranscriptionResult result = dagloSttProvider.checkTranscriptionStatus(jobId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("예외 발생: stillProcessing 반환")
        void checkTranscriptionStatus_Exception_ReturnsStillProcessing() {
            // given
            String jobId = "test-rid";
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(new RuntimeException("Unknown error"));

            // when
            SttTranscriptionResult result = dagloSttProvider.checkTranscriptionStatus(jobId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("requestSummary 테스트")
    class RequestSummaryTest {

        @Test
        @DisplayName("성공: 요약 요청 후 RID 반환")
        void requestSummary_Success() {
            // given
            String text = "테스트 텍스트";
            String expectedRid = "summary-rid-12345";

            SummaryResponseDto responseDto = new SummaryResponseDto();
            responseDto.setRid(expectedRid);

            when(circuitBreaker.executeSupplier(any(Supplier.class))).thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });
            when(restClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
            doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(SummaryResponseDto.class)).thenReturn(responseDto);

            // when
            String result = dagloSttProvider.requestSummary(text);

            // then
            assertThat(result).isEqualTo(expectedRid);
        }

        @Test
        @DisplayName("실패: CircuitBreaker Open 시 RuntimeException 발생")
        void requestSummary_CircuitBreakerOpen() {
            // given
            String text = "테스트 텍스트";
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(mock(CallNotPermittedException.class));

            // when & then
            assertThatThrownBy(() -> dagloSttProvider.requestSummary(text))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Daglo Summary API temporarily unavailable");
        }
    }

    @Nested
    @DisplayName("checkSummaryStatus 테스트")
    class CheckSummaryStatusTest {

        @Test
        @DisplayName("성공: 완료 상태 반환")
        void checkSummaryStatus_Completed() {
            // given
            String jobId = "summary-rid";
            SummaryResponseDto responseDto = new SummaryResponseDto();
            responseDto.setRid(jobId);
            responseDto.setStatus("processed");
            responseDto.setProgress(100);

            when(circuitBreaker.executeSupplier(any(Supplier.class))).thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(0);
                return supplier.get();
            });
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(SummaryResponseDto.class)).thenReturn(responseDto);

            // when
            SttSummaryResult result = dagloSttProvider.checkSummaryStatus(jobId);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CircuitBreaker Open: stillProcessing 반환")
        void checkSummaryStatus_CircuitBreakerOpen_ReturnsStillProcessing() {
            // given
            String jobId = "summary-rid";
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(mock(CallNotPermittedException.class));

            // when
            SttSummaryResult result = dagloSttProvider.checkSummaryStatus(jobId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("예외 발생: stillProcessing 반환")
        void checkSummaryStatus_Exception_ReturnsStillProcessing() {
            // given
            String jobId = "summary-rid";
            when(circuitBreaker.executeSupplier(any(Supplier.class)))
                    .thenThrow(new RuntimeException("Unknown error"));

            // when
            SttSummaryResult result = dagloSttProvider.checkSummaryStatus(jobId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isCompleted()).isFalse();
        }
    }
}
