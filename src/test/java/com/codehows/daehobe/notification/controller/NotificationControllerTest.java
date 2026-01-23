package com.codehows.daehobe.notification.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.notification.dto.NotificationResponseDto;
import com.codehows.daehobe.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(JwtService.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private final Long TEST_MEMBER_ID = 1L;

    @Test
    @DisplayName("성공: 내 알림 조회")
    @WithMockUser(username = "1")
    void getMyNotifications_Success() throws Exception {
        // given
        NotificationResponseDto dto = NotificationResponseDto.builder().message("알림 메시지").build();
        PageImpl<NotificationResponseDto> page = new PageImpl<>(Collections.singletonList(dto));
        given(notificationService.getMyNotifications(anyLong(), anyInt(), anyInt())).willReturn(page);

        // when
        ResultActions result = mockMvc.perform(get("/notifications")
                .param("page", "0")
                .param("size", "5")
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("알림 메시지"));
    }
    
    @Test
    @DisplayName("실패: 내 알림 조회 중 오류 발생")
    @WithMockUser(username = "1")
    void getMyNotifications_Error() throws Exception {
        // given
        given(notificationService.getMyNotifications(anyLong(), anyInt(), anyInt()))
                .willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions result = mockMvc.perform(get("/notifications")
                .param("page", "0")
                .param("size", "5")
                .with(csrf()));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("성공: 알림 읽음 처리")
    @WithMockUser
    void readNotification_Success() throws Exception {
        // given
        Long notificationId = 1L;
        doNothing().when(notificationService).readNotification(notificationId);

        // when
        ResultActions result = mockMvc.perform(patch("/notifications/{id}/read", notificationId).with(csrf()));

        // then
        result.andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("실패: 알림 읽음 처리 중 오류 발생")
    @WithMockUser
    void readNotification_Error() throws Exception {
        // given
        Long notificationId = 1L;
        doThrow(new RuntimeException("서비스 오류")).when(notificationService).readNotification(notificationId);

        // when
        ResultActions result = mockMvc.perform(patch("/notifications/{id}/read", notificationId).with(csrf()));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("성공: 읽지 않은 알림 수 조회")
    @WithMockUser(username = "1")
    void getUnreadNotificationCount_Success() throws Exception {
        // given
        given(notificationService.getUnreadCount(anyLong())).willReturn(3);

        // when
        ResultActions result = mockMvc.perform(get("/notifications/unread-count").with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));
    }
    
    @Test
    @DisplayName("실패: 읽지 않은 알림 수 조회 중 오류 발생")
    @WithMockUser(username = "1")
    void getUnreadNotificationCount_Error() throws Exception {
        // given
        given(notificationService.getUnreadCount(anyLong())).willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions result = mockMvc.perform(get("/notifications/unread-count").with(csrf()));

        // then
        result.andExpect(status().isBadRequest());
    }
}
