package com.codehows.daehobe.notification.webPush.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.InvalidSubscriptionException;
import com.codehows.daehobe.notification.webPush.service.WebPushService;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebPushController.class)
@ExtendWith(PerformanceLoggingExtension.class)
class WebPushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebPushService webPushService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("성공: 푸시 구독")
    @WithMockUser(username = "1")
    void subscribe_Success() throws Exception {
        // given
        PushSubscriptionDto subscription = createValidSubscription();
        doNothing().when(webPushService).saveSubscription(any(PushSubscriptionDto.class), eq("1"));

        // when & then
        mockMvc.perform(post("/push/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscription)))
                .andExpect(status().isOk());

        verify(webPushService).saveSubscription(any(PushSubscriptionDto.class), eq("1"));
    }

    @Test
    @DisplayName("실패: 유효하지 않은 구독 정보 - BadRequest 반환")
    @WithMockUser(username = "1")
    void subscribe_InvalidSubscription_BadRequest() throws Exception {
        // given
        PushSubscriptionDto subscription = createValidSubscription();
        doThrow(new InvalidSubscriptionException("Invalid endpoint"))
                .when(webPushService).saveSubscription(any(PushSubscriptionDto.class), eq("1"));

        // when & then
        mockMvc.perform(post("/push/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscription)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: 일반 예외 발생 - BadRequest 반환")
    @WithMockUser(username = "1")
    void subscribe_GeneralException_BadRequest() throws Exception {
        // given
        PushSubscriptionDto subscription = createValidSubscription();
        doThrow(new RuntimeException("Unexpected error"))
                .when(webPushService).saveSubscription(any(PushSubscriptionDto.class), eq("1"));

        // when & then
        mockMvc.perform(post("/push/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscription)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: 인증되지 않은 사용자 - Unauthorized")
    void subscribe_Unauthorized() throws Exception {
        // given
        PushSubscriptionDto subscription = createValidSubscription();

        // when & then
        mockMvc.perform(post("/push/subscribe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subscription)))
                .andExpect(status().isUnauthorized());
    }

    private PushSubscriptionDto createValidSubscription() {
        PushSubscriptionDto dto = new PushSubscriptionDto();
        dto.setEndpoint("https://fcm.googleapis.com/fcm/send/abc123");
        dto.setMemberId("1");
        PushSubscriptionDto.Keys keys = new PushSubscriptionDto.Keys();
        keys.setP256dh("BLcmqL3J5aXm7t...");
        keys.setAuth("auth123456");
        dto.setKeys(keys);
        return dto;
    }
}
