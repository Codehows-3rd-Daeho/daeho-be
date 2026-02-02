package com.codehows.daehobe.stt.service.cache;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class SttCacheServiceTest {

    @Mock
    private StringRedisTemplate hashRedisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private SttCacheService sttCacheService;

    @BeforeEach
    void setUp() {
        lenient().when(hashRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(hashRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        sttCacheService = new SttCacheService(hashRedisTemplate, objectMapper);
        ReflectionTestUtils.setField(sttCacheService, "staleThresholdMinutes", 60L);
    }

    @Nested
    @DisplayName("cacheSttStatus 테스트")
    class CacheSttStatusTest {

        @Test
        @DisplayName("성공: RECORDING 상태 캐싱 - TTL 60분")
        void cacheSttStatus_Recording_Ttl60Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.RECORDING)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(60L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("성공: ENCODING 상태 캐싱 - TTL 60분")
        void cacheSttStatus_Encoding_Ttl60Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.ENCODING)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(60L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("성공: ENCODED 상태 캐싱 - TTL 1440분 (24시간)")
        void cacheSttStatus_Encoded_Ttl1440Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.ENCODED)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(1440L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("성공: PROCESSING 상태 캐싱 - TTL 30분")
        void cacheSttStatus_Processing_Ttl30Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.PROCESSING)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(30L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("성공: SUMMARIZING 상태 캐싱 - TTL 30분")
        void cacheSttStatus_Summarizing_Ttl30Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.SUMMARIZING)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(30L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("성공: COMPLETED 상태 캐싱 - TTL 10분")
        void cacheSttStatus_Completed_Ttl10Minutes() throws Exception {
            // given
            STTDto sttDto = STTDto.builder()
                    .id(1L)
                    .status(STT.Status.COMPLETED)
                    .build();
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations).set(
                    eq("stt:status:1"),
                    anyString(),
                    eq(10L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("실패: 직렬화 오류 시 로깅만 수행")
        void cacheSttStatus_SerializationError_LogsOnly() throws Exception {
            // given
            STTDto sttDto = STTDto.builder().id(1L).status(STT.Status.RECORDING).build();
            when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

            // when
            sttCacheService.cacheSttStatus(sttDto);

            // then
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("getCachedSttStatus 테스트")
    class GetCachedSttStatusTest {

        @Test
        @DisplayName("성공: 캐시 히트")
        void getCachedSttStatus_CacheHit() throws Exception {
            // given
            Long sttId = 1L;
            String jsonValue = "{\"id\":1,\"status\":\"PROCESSING\"}";
            STTDto expectedDto = STTDto.builder().id(sttId).status(STT.Status.PROCESSING).build();

            when(valueOperations.get("stt:status:" + sttId)).thenReturn(jsonValue);
            when(objectMapper.readValue(jsonValue, STTDto.class)).thenReturn(expectedDto);

            // when
            STTDto result = sttCacheService.getCachedSttStatus(sttId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(sttId);
            assertThat(result.getStatus()).isEqualTo(STT.Status.PROCESSING);
        }

        @Test
        @DisplayName("성공: 캐시 미스 - null 반환")
        void getCachedSttStatus_CacheMiss() {
            // given
            Long sttId = 1L;
            when(valueOperations.get("stt:status:" + sttId)).thenReturn(null);

            // when
            STTDto result = sttCacheService.getCachedSttStatus(sttId);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("실패: 역직렬화 오류 시 null 반환")
        void getCachedSttStatus_DeserializationError() throws Exception {
            // given
            Long sttId = 1L;
            String invalidJson = "invalid json";
            when(valueOperations.get("stt:status:" + sttId)).thenReturn(invalidJson);
            when(objectMapper.readValue(invalidJson, STTDto.class)).thenThrow(new JsonProcessingException("Error") {});

            // when
            STTDto result = sttCacheService.getCachedSttStatus(sttId);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Polling Set 테스트")
    class PollingSetTest {

        @Test
        @DisplayName("성공: PROCESSING 상태로 폴링 셋에 추가")
        void addToPollingSet_Processing() {
            // given
            Long sttId = 1L;

            // when
            sttCacheService.addToPollingSet(sttId, STT.Status.PROCESSING);

            // then
            verify(zSetOperations).add(eq(STT_POLLING_PROCESSING_SET), eq("1"), anyDouble());
        }

        @Test
        @DisplayName("성공: SUMMARIZING 상태로 폴링 셋에 추가")
        void addToPollingSet_Summarizing() {
            // given
            Long sttId = 1L;

            // when
            sttCacheService.addToPollingSet(sttId, STT.Status.SUMMARIZING);

            // then
            verify(zSetOperations).add(eq(STT_POLLING_SUMMARIZING_SET), eq("1"), anyDouble());
        }

        @Test
        @DisplayName("무시: RECORDING 상태는 폴링 셋에 추가되지 않음")
        void addToPollingSet_Recording_Ignored() {
            // given
            Long sttId = 1L;

            // when
            sttCacheService.addToPollingSet(sttId, STT.Status.RECORDING);

            // then
            verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("성공: 폴링 셋에서 제거")
        void removeFromPollingSet_Processing() {
            // given
            Long sttId = 1L;

            // when
            sttCacheService.removeFromPollingSet(sttId, STT.Status.PROCESSING);

            // then
            verify(zSetOperations).remove(STT_POLLING_PROCESSING_SET, "1");
        }

        @Test
        @DisplayName("성공: 폴링 태스크 ID 조회")
        void getPollingTaskIds_Success() {
            // given
            Set<String> members = Set.of("1", "2", "3");
            when(zSetOperations.range(STT_POLLING_PROCESSING_SET, 0, -1)).thenReturn(members);

            // when
            Set<Long> result = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

            // then
            assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
        }

        @Test
        @DisplayName("성공: 빈 폴링 셋")
        void getPollingTaskIds_Empty() {
            // given
            when(zSetOperations.range(STT_POLLING_PROCESSING_SET, 0, -1)).thenReturn(Collections.emptySet());

            // when
            Set<Long> result = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: Redis 오류 시 빈 집합 반환")
        void getPollingTaskIds_RedisError() {
            // given
            when(zSetOperations.range(STT_POLLING_PROCESSING_SET, 0, -1))
                    .thenThrow(new RuntimeException("Redis error"));

            // when
            Set<Long> result = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Retry Count 테스트")
    class RetryCountTest {

        @Test
        @DisplayName("성공: 재시도 카운트 증가")
        void incrementRetryCount_Success() {
            // given
            Long sttId = 1L;
            when(valueOperations.increment(STT_RETRY_COUNT_PREFIX + sttId)).thenReturn(3L);
            when(hashRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // when
            int result = sttCacheService.incrementRetryCount(sttId);

            // then
            assertThat(result).isEqualTo(3);
            verify(hashRedisTemplate).expire(STT_RETRY_COUNT_PREFIX + sttId, 30, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("성공: 재시도 카운트 조회")
        void getRetryCount_Success() {
            // given
            Long sttId = 1L;
            when(valueOperations.get(STT_RETRY_COUNT_PREFIX + sttId)).thenReturn("5");

            // when
            Integer result = sttCacheService.getRetryCount(sttId);

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("성공: 재시도 카운트 없으면 0 반환")
        void getRetryCount_NotExists_ReturnsZero() {
            // given
            Long sttId = 1L;
            when(valueOperations.get(STT_RETRY_COUNT_PREFIX + sttId)).thenReturn(null);

            // when
            Integer result = sttCacheService.getRetryCount(sttId);

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: 재시도 카운트 리셋")
        void resetRetryCount_Success() {
            // given
            Long sttId = 1L;
            when(hashRedisTemplate.delete(STT_RETRY_COUNT_PREFIX + sttId)).thenReturn(true);

            // when
            sttCacheService.resetRetryCount(sttId);

            // then
            verify(hashRedisTemplate).delete(STT_RETRY_COUNT_PREFIX + sttId);
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    class UtilityMethodsTest {

        @Test
        @DisplayName("성공: Redis 가용성 확인 - 가용")
        void isRedisAvailable_Available() {
            // given
            when(hashRedisTemplate.hasKey("health-check")).thenReturn(false);

            // when
            boolean result = sttCacheService.isRedisAvailable();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("실패: Redis 가용성 확인 - 불가용")
        void isRedisAvailable_Unavailable() {
            // given
            when(hashRedisTemplate.hasKey("health-check")).thenThrow(new RuntimeException("Connection refused"));

            // when
            boolean result = sttCacheService.isRedisAvailable();

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("성공: 오래된 폴링 태스크 정리")
        void cleanupStalePollingTasks_Success() {
            // given
            when(zSetOperations.removeRangeByScore(eq(STT_POLLING_PROCESSING_SET), eq(0D), anyDouble())).thenReturn(2L);
            when(zSetOperations.removeRangeByScore(eq(STT_POLLING_SUMMARIZING_SET), eq(0D), anyDouble())).thenReturn(1L);

            // when
            sttCacheService.cleanupStalePollingTasks();

            // then
            verify(zSetOperations).removeRangeByScore(eq(STT_POLLING_PROCESSING_SET), eq(0D), anyDouble());
            verify(zSetOperations).removeRangeByScore(eq(STT_POLLING_SUMMARIZING_SET), eq(0D), anyDouble());
        }
    }
}
