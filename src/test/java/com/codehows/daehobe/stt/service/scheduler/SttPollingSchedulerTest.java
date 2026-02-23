package com.codehows.daehobe.stt.service.scheduler;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.STTService;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.processing.SttJobProcessor;
import com.codehows.daehobe.stt.service.processing.SttPollingScheduler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class SttPollingSchedulerTest {

    @Mock
    private STTRepository sttRepository;
    @Mock
    private SttJobProcessor sttJobProcessor;
    @Mock
    private SttCacheService sttCacheService;
    @Mock
    private STTService sttService;
    @Mock
    private StringRedisTemplate redisTemplate;

    private SttPollingScheduler sttPollingScheduler;

    @BeforeEach
    void setUp() {
        sttPollingScheduler = new SttPollingScheduler(sttRepository, sttJobProcessor, sttCacheService, sttService, redisTemplate);
        ReflectionTestUtils.setField(sttPollingScheduler, "maxAttempts", 150);
    }

    @Nested
    @DisplayName("pollProcessingTasks 테스트")
    class PollProcessingTasksTest {

        @Test
        @DisplayName("성공: 정상 처리")
        void pollProcessingTasks_Success() {
            // given
            Set<Long> taskIds = Set.of(1L, 2L, 3L);
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttJobProcessor, times(3)).processSingleSttJob(anyLong());
        }

        @Test
        @DisplayName("재시도: SttNotCompletedException 시 retry count 증가")
        void pollProcessingTasks_NotCompleted_RetryIncremented() {
            // given
            Long sttId = 1L;
            Set<Long> taskIds = Set.of(sttId);
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);
            doThrow(new SttNotCompletedException("Not completed"))
                    .when(sttJobProcessor).processSingleSttJob(sttId);
            when(sttCacheService.incrementRetryCount(sttId)).thenReturn(5);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttCacheService).incrementRetryCount(sttId);
        }

        @Test
        @DisplayName("최대 재시도 초과: ENCODED 상태로 롤백")
        void pollProcessingTasks_MaxRetryExceeded_RollbackToEncoded() {
            // given
            Long sttId = 1L;
            Set<Long> taskIds = Set.of(sttId);
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.PROCESSING)
                    .build();

            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);
            doThrow(new SttNotCompletedException("Not completed"))
                    .when(sttJobProcessor).processSingleSttJob(sttId);
            when(sttCacheService.incrementRetryCount(sttId)).thenReturn(150); // maxAttempts 도달
            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttCacheService).removeFromPollingSet(sttId, STT.Status.PROCESSING);
            verify(sttCacheService).resetRetryCount(sttId);
            verify(sttCacheService).cacheSttStatus(argThat(dto ->
                    dto.getStatus() == STT.Status.ENCODED
            ));
        }

        @Test
        @DisplayName("예외 처리: 일반 예외 시 로깅만 수행")
        void pollProcessingTasks_GeneralException_LogOnly() {
            // given
            Long sttId = 1L;
            Set<Long> taskIds = Set.of(sttId);
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);
            doThrow(new RuntimeException("Unknown error"))
                    .when(sttJobProcessor).processSingleSttJob(sttId);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttCacheService, never()).incrementRetryCount(anyLong());
        }

        @Test
        @DisplayName("빈 태스크: 폴링 셋이 비어있으면 처리 없음")
        void pollProcessingTasks_EmptyTasks_NoProcessing() {
            // given
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(Collections.emptySet());
            when(sttCacheService.isRedisAvailable()).thenReturn(true);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttJobProcessor, never()).processSingleSttJob(anyLong());
        }
    }

    @Nested
    @DisplayName("pollSummarizingTasks 테스트")
    class PollSummarizingTasksTest {

        @Test
        @DisplayName("성공: 정상 폴링")
        void pollSummarizingTasks_Success() {
            // given
            Set<Long> taskIds = Set.of(1L, 2L);
            when(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);

            // when
            sttPollingScheduler.pollSummarizingTasks();

            // then
            verify(sttJobProcessor, times(2)).processSingleSummaryJob(anyLong());
        }

        @Test
        @DisplayName("재시도: SttNotCompletedException 시 retry count 증가")
        void pollSummarizingTasks_NotCompleted_RetryIncremented() {
            // given
            Long sttId = 1L;
            Set<Long> taskIds = Set.of(sttId);
            when(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);
            doThrow(new SttNotCompletedException("Not completed"))
                    .when(sttJobProcessor).processSingleSummaryJob(sttId);
            when(sttCacheService.incrementRetryCount(sttId)).thenReturn(10);

            // when
            sttPollingScheduler.pollSummarizingTasks();

            // then
            verify(sttCacheService).incrementRetryCount(sttId);
        }

        @Test
        @DisplayName("최대 재시도 초과: ENCODED 상태로 롤백")
        void pollSummarizingTasks_MaxRetryExceeded_RollbackToEncoded() {
            // given
            Long sttId = 1L;
            Set<Long> taskIds = Set.of(sttId);
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.SUMMARIZING)
                    .build();

            when(sttCacheService.getPollingTaskIds(STT.Status.SUMMARIZING)).thenReturn(taskIds);
            when(sttCacheService.isRedisAvailable()).thenReturn(true);
            doThrow(new SttNotCompletedException("Not completed"))
                    .when(sttJobProcessor).processSingleSummaryJob(sttId);
            when(sttCacheService.incrementRetryCount(sttId)).thenReturn(150);
            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttPollingScheduler.pollSummarizingTasks();

            // then
            verify(sttCacheService).removeFromPollingSet(sttId, STT.Status.SUMMARIZING);
            verify(sttCacheService).resetRetryCount(sttId);
            verify(sttCacheService).cacheSttStatus(argThat(dto ->
                    dto.getStatus() == STT.Status.ENCODED
            ));
        }
    }

    @Nested
    @DisplayName("Fallback 테스트")
    class FallbackTest {

        @Test
        @DisplayName("Redis 불가용 시 DB fallback")
        void getTaskIdsWithFallback_RedisUnavailable_DbFallback() {
            // given
            Set<Long> dbTaskIds = Set.of(1L, 2L);
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(Collections.emptySet());
            when(sttCacheService.isRedisAvailable()).thenReturn(false);
            when(sttRepository.findIdsByStatus(STT.Status.PROCESSING)).thenReturn(dbTaskIds);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttRepository).findIdsByStatus(STT.Status.PROCESSING);
            verify(sttJobProcessor, times(2)).processSingleSttJob(anyLong());
        }

        @Test
        @DisplayName("Redis 가용하면 DB fallback 안함")
        void getTaskIdsWithFallback_RedisAvailable_NoDbFallback() {
            // given
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(Collections.emptySet());
            when(sttCacheService.isRedisAvailable()).thenReturn(true);

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttRepository, never()).findIdsByStatus(any());
        }

        @Test
        @DisplayName("DB도 불가용 시 조용히 실패")
        void getTaskIdsWithFallback_BothUnavailable_SilentFailure() {
            // given
            when(sttCacheService.getPollingTaskIds(STT.Status.PROCESSING)).thenReturn(Collections.emptySet());
            when(sttCacheService.isRedisAvailable()).thenReturn(false);
            when(sttRepository.findIdsByStatus(STT.Status.PROCESSING))
                    .thenThrow(new RuntimeException("DB unavailable"));

            // when
            sttPollingScheduler.pollProcessingTasks();

            // then
            verify(sttJobProcessor, never()).processSingleSttJob(anyLong());
        }
    }
}
