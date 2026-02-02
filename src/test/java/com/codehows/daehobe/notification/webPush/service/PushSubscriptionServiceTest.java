package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.InvalidSubscriptionException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class PushSubscriptionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PushSubscriptionService pushSubscriptionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        pushSubscriptionService = new PushSubscriptionService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(pushSubscriptionService, "subscriptionTtlDays", 7);
    }

    @Nested
    @DisplayName("saveSubscription 테스트")
    class SaveSubscriptionTest {

        @Test
        @DisplayName("성공: 유효한 구독 저장")
        void saveSubscription_Success() throws Exception {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = createValidSubscription();
            when(objectMapper.writeValueAsString(dto)).thenReturn("{\"endpoint\":\"...\"}");

            // when
            pushSubscriptionService.saveSubscription(dto, memberId);

            // then
            verify(valueOperations).set(
                    eq("web-push:subscription:" + memberId),
                    anyString(),
                    eq(Duration.ofDays(7))
            );
        }

        @Test
        @DisplayName("실패: null 구독")
        void saveSubscription_NullSubscription() {
            // given
            String memberId = "1";

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(null, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("실패: null endpoint")
        void saveSubscription_NullEndpoint() {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = new PushSubscriptionDto();
            dto.setEndpoint(null);
            PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
            keys.setP256dh("key");
            keys.setAuth("auth");
            dto.setKeys(keys);

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("Invalid endpoint");
        }

        @Test
        @DisplayName("실패: HTTP endpoint (HTTPS만 허용)")
        void saveSubscription_HttpEndpoint() {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = createValidSubscription();
            dto.setEndpoint("http://push.example.com/send");

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("Invalid endpoint");
        }

        @Test
        @DisplayName("실패: keys가 null")
        void saveSubscription_NullKeys() {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = new PushSubscriptionDto();
            dto.setEndpoint("https://push.example.com/send");
            dto.setKeys(null);

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("Missing encryption keys");
        }

        @Test
        @DisplayName("실패: p256dh가 null 또는 빈 문자열")
        void saveSubscription_NullP256dh() {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = new PushSubscriptionDto();
            dto.setEndpoint("https://push.example.com/send");
            PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
            keys.setP256dh(null);
            keys.setAuth("auth");
            dto.setKeys(keys);

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("Missing p256dh key");
        }

        @Test
        @DisplayName("실패: auth가 null 또는 빈 문자열")
        void saveSubscription_NullAuth() {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = new PushSubscriptionDto();
            dto.setEndpoint("https://push.example.com/send");
            PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
            keys.setP256dh("p256dh");
            keys.setAuth("");
            dto.setKeys(keys);

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(InvalidSubscriptionException.class)
                    .hasMessageContaining("Missing auth key");
        }

        @Test
        @DisplayName("실패: JSON 직렬화 오류")
        void saveSubscription_SerializationError() throws Exception {
            // given
            String memberId = "1";
            PushSubscriptionDto dto = createValidSubscription();
            when(objectMapper.writeValueAsString(dto)).thenThrow(new JsonProcessingException("Error") {});

            // when & then
            assertThatThrownBy(() -> pushSubscriptionService.saveSubscription(dto, memberId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save subscription");
        }
    }

    @Nested
    @DisplayName("getSubscription 테스트")
    class GetSubscriptionTest {

        @Test
        @DisplayName("성공: 구독 조회")
        void getSubscription_Success() throws Exception {
            // given
            String memberId = "1";
            String json = "{\"endpoint\":\"https://...\"}";
            PushSubscriptionDto expectedDto = createValidSubscription();

            when(valueOperations.get("web-push:subscription:" + memberId)).thenReturn(json);
            when(objectMapper.readValue(json, PushSubscriptionDto.class)).thenReturn(expectedDto);

            // when
            Optional<PushSubscriptionDto> result = pushSubscriptionService.getSubscription(memberId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getEndpoint()).isEqualTo(expectedDto.getEndpoint());
        }

        @Test
        @DisplayName("성공: 구독 없음 - Optional.empty 반환")
        void getSubscription_NotFound() {
            // given
            String memberId = "1";
            when(valueOperations.get("web-push:subscription:" + memberId)).thenReturn(null);

            // when
            Optional<PushSubscriptionDto> result = pushSubscriptionService.getSubscription(memberId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 역직렬화 오류 시 Optional.empty 반환")
        void getSubscription_DeserializationError() throws Exception {
            // given
            String memberId = "1";
            String json = "invalid json";
            when(valueOperations.get("web-push:subscription:" + memberId)).thenReturn(json);
            when(objectMapper.readValue(json, PushSubscriptionDto.class))
                    .thenThrow(new JsonProcessingException("Error") {});

            // when
            Optional<PushSubscriptionDto> result = pushSubscriptionService.getSubscription(memberId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteSubscription 테스트")
    class DeleteSubscriptionTest {

        @Test
        @DisplayName("성공: 구독 삭제")
        void deleteSubscription_Success() {
            // given
            String memberId = "1";
            when(redisTemplate.delete("web-push:subscription:" + memberId)).thenReturn(true);

            // when
            pushSubscriptionService.deleteSubscription(memberId);

            // then
            verify(redisTemplate).delete("web-push:subscription:" + memberId);
        }
    }

    @Nested
    @DisplayName("hasSubscription 테스트")
    class HasSubscriptionTest {

        @Test
        @DisplayName("성공: 구독 존재")
        void hasSubscription_Exists() {
            // given
            String memberId = "1";
            when(redisTemplate.hasKey("web-push:subscription:" + memberId)).thenReturn(true);

            // when
            boolean result = pushSubscriptionService.hasSubscription(memberId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("성공: 구독 없음")
        void hasSubscription_NotExists() {
            // given
            String memberId = "1";
            when(redisTemplate.hasKey("web-push:subscription:" + memberId)).thenReturn(false);

            // when
            boolean result = pushSubscriptionService.hasSubscription(memberId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("refreshSubscriptionTtl 테스트")
    class RefreshSubscriptionTtlTest {

        @Test
        @DisplayName("성공: TTL 갱신 (키 존재)")
        void refreshSubscriptionTtl_KeyExists() {
            // given
            String memberId = "1";
            when(redisTemplate.hasKey("web-push:subscription:" + memberId)).thenReturn(true);
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            // when
            pushSubscriptionService.refreshSubscriptionTtl(memberId);

            // then
            verify(redisTemplate).expire("web-push:subscription:" + memberId, Duration.ofDays(7));
        }

        @Test
        @DisplayName("무시: 키 없으면 갱신 안함")
        void refreshSubscriptionTtl_KeyNotExists() {
            // given
            String memberId = "1";
            when(redisTemplate.hasKey("web-push:subscription:" + memberId)).thenReturn(false);

            // when
            pushSubscriptionService.refreshSubscriptionTtl(memberId);

            // then
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }
    }

    private PushSubscriptionDto createValidSubscription() {
        PushSubscriptionDto dto = new PushSubscriptionDto();
        dto.setEndpoint("https://push.example.com/send/abc123");
        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
        keys.setP256dh("BLcmqL3J...");
        keys.setAuth("auth123");
        dto.setKeys(keys);
        return dto;
    }
}
