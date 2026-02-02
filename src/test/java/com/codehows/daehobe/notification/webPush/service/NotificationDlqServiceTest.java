package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.notification.dto.FailedNotificationDto;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class NotificationDlqServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private WebPushSender webPushSender;
    @Mock
    private ListOperations<String, Object> listOperations;

    private NotificationDlqService notificationDlqService;

    private static final String DLQ_KEY = "notification:dlq";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        notificationDlqService = new NotificationDlqService(redisTemplate, objectMapper, webPushSender);
        ReflectionTestUtils.setField(notificationDlqService, "batchSize", 50);
    }

    @Nested
    @DisplayName("saveToDeadLetterQueue 테스트")
    class SaveToDeadLetterQueueTest {

        @Test
        @DisplayName("성공: DLQ에 저장 (파라미터 방식)")
        void saveToDeadLetterQueue_WithParams_Success() throws Exception {
            // given
            String memberId = "1";
            NotificationMessageDto messageDto = new NotificationMessageDto("메시지", "/url");
            int statusCode = 500;
            String errorMessage = "Server error";

            when(objectMapper.writeValueAsString(any(FailedNotificationDto.class))).thenReturn("{\"json\"}");

            // when
            notificationDlqService.saveToDeadLetterQueue(memberId, messageDto, statusCode, errorMessage);

            // then
            verify(listOperations).rightPush(eq(DLQ_KEY), anyString());
        }

        @Test
        @DisplayName("성공: DLQ에 저장 (DTO 방식)")
        void saveToDeadLetterQueue_WithDto_Success() throws Exception {
            // given
            FailedNotificationDto failedNotification = FailedNotificationDto.builder()
                    .memberId("1")
                    .message("메시지")
                    .url("/url")
                    .statusCode(500)
                    .errorMessage("Error")
                    .failedAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();

            when(objectMapper.writeValueAsString(failedNotification)).thenReturn("{\"json\"}");

            // when
            notificationDlqService.saveToDeadLetterQueue(failedNotification);

            // then
            verify(listOperations).rightPush(eq(DLQ_KEY), anyString());
        }

        @Test
        @DisplayName("실패: JSON 직렬화 오류 시 로깅만 수행")
        void saveToDeadLetterQueue_SerializationError() throws Exception {
            // given
            String memberId = "1";
            NotificationMessageDto messageDto = new NotificationMessageDto("메시지", "/url");
            when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

            // when
            notificationDlqService.saveToDeadLetterQueue(memberId, messageDto, 500, "Error");

            // then
            verify(listOperations, never()).rightPush(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("getDlqSize 테스트")
    class GetDlqSizeTest {

        @Test
        @DisplayName("성공: DLQ 크기 조회")
        void getDlqSize_Success() {
            // given
            when(listOperations.size(DLQ_KEY)).thenReturn(10L);

            // when
            Long result = notificationDlqService.getDlqSize();

            // then
            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("peekDlqItems 테스트")
    class PeekDlqItemsTest {

        @Test
        @DisplayName("성공: DLQ 아이템 조회")
        void peekDlqItems_Success() throws Exception {
            // given
            String json1 = "{\"memberId\":\"1\"}";
            String json2 = "{\"memberId\":\"2\"}";
            FailedNotificationDto dto1 = FailedNotificationDto.builder().memberId("1").build();
            FailedNotificationDto dto2 = FailedNotificationDto.builder().memberId("2").build();

            when(listOperations.range(DLQ_KEY, 0, 9)).thenReturn(Arrays.asList(json1, json2));
            when(objectMapper.readValue(json1, FailedNotificationDto.class)).thenReturn(dto1);
            when(objectMapper.readValue(json2, FailedNotificationDto.class)).thenReturn(dto2);

            // when
            List<FailedNotificationDto> result = notificationDlqService.peekDlqItems(10);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공: DLQ가 비어있으면 빈 리스트 반환")
        void peekDlqItems_Empty() {
            // given
            when(listOperations.range(DLQ_KEY, 0, 9)).thenReturn(null);

            // when
            List<FailedNotificationDto> result = notificationDlqService.peekDlqItems(10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("processDlqBatch 테스트")
    class ProcessDlqBatchTest {

        @Test
        @DisplayName("성공: 배치 재처리 - 재시도 성공")
        void processDlqBatch_RetrySuccess() throws Exception {
            // given
            String json = "{\"memberId\":\"1\",\"retryCount\":0}";
            FailedNotificationDto dto = FailedNotificationDto.builder()
                    .memberId("1")
                    .message("메시지")
                    .url("/url")
                    .statusCode(500)
                    .retryCount(0)
                    .build();

            when(listOperations.size(DLQ_KEY)).thenReturn(1L);
            when(listOperations.leftPop(DLQ_KEY)).thenReturn(json);
            when(objectMapper.readValue(json, FailedNotificationDto.class)).thenReturn(dto);
            doNothing().when(webPushSender).sendPushNotification(anyString(), any());

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(webPushSender).sendPushNotification(eq("1"), any(NotificationMessageDto.class));
            verify(listOperations, never()).rightPush(anyString(), anyString()); // 성공하면 다시 저장 안함
        }

        @Test
        @DisplayName("실패: 배치 재처리 - 재시도 실패 시 다시 DLQ에 저장")
        void processDlqBatch_RetryFailed_RequeueWithIncrementedCount() throws Exception {
            // given
            String json = "{\"memberId\":\"1\",\"retryCount\":0}";
            FailedNotificationDto dto = FailedNotificationDto.builder()
                    .memberId("1")
                    .message("메시지")
                    .url("/url")
                    .statusCode(500)
                    .retryCount(0)
                    .build();

            when(listOperations.size(DLQ_KEY)).thenReturn(1L);
            when(listOperations.leftPop(DLQ_KEY)).thenReturn(json);
            when(objectMapper.readValue(json, FailedNotificationDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("Push failed")).when(webPushSender).sendPushNotification(anyString(), any());
            when(objectMapper.writeValueAsString(any(FailedNotificationDto.class))).thenReturn("{\"json\"}");

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(listOperations).rightPush(eq(DLQ_KEY), anyString()); // 실패하면 다시 저장
        }

        @Test
        @DisplayName("무시: 410 상태 코드는 재시도하지 않음")
        void processDlqBatch_Status410_NoRetry() throws Exception {
            // given
            String json = "{\"memberId\":\"1\",\"statusCode\":410}";
            FailedNotificationDto dto = FailedNotificationDto.builder()
                    .memberId("1")
                    .message("메시지")
                    .url("/url")
                    .statusCode(410) // 구독 만료
                    .retryCount(0)
                    .build();

            when(listOperations.size(DLQ_KEY)).thenReturn(1L);
            when(listOperations.leftPop(DLQ_KEY)).thenReturn(json);
            when(objectMapper.readValue(json, FailedNotificationDto.class)).thenReturn(dto);

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(webPushSender, never()).sendPushNotification(anyString(), any());
        }

        @Test
        @DisplayName("최대 재시도 초과: 영구 실패로 처리")
        void processDlqBatch_MaxRetryExceeded_PermanentFail() throws Exception {
            // given
            String json = "{\"memberId\":\"1\",\"retryCount\":5}";
            FailedNotificationDto dto = FailedNotificationDto.builder()
                    .memberId("1")
                    .message("메시지")
                    .url("/url")
                    .statusCode(500)
                    .retryCount(5) // MAX_RETRY_COUNT = 5
                    .build();

            when(listOperations.size(DLQ_KEY)).thenReturn(1L);
            when(listOperations.leftPop(DLQ_KEY)).thenReturn(json);
            when(objectMapper.readValue(json, FailedNotificationDto.class)).thenReturn(dto);

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(webPushSender, never()).sendPushNotification(anyString(), any());
            verify(listOperations, never()).rightPush(anyString(), anyString()); // 영구 실패면 다시 저장 안함
        }

        @Test
        @DisplayName("무시: DLQ가 비어있으면 처리 안함")
        void processDlqBatch_EmptyQueue_NoProcessing() {
            // given
            when(listOperations.size(DLQ_KEY)).thenReturn(0L);

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(listOperations, never()).leftPop(anyString());
        }

        @Test
        @DisplayName("무시: DLQ 크기가 null이면 처리 안함")
        void processDlqBatch_NullSize_NoProcessing() {
            // given
            when(listOperations.size(DLQ_KEY)).thenReturn(null);

            // when
            notificationDlqService.processDlqBatch();

            // then
            verify(listOperations, never()).leftPop(anyString());
        }
    }
}
