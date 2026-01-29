package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.masterData.service.SetNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(SetNotificationController.class)
@Import(JwtService.class)
class SetNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SetNotificationService setNotificationService;

    private final String API_PATH = "/admin/notificationSetting";

    @Test
    @DisplayName("성공: 알림 설정 저장")
    @WithMockUser
    void saveSettings_Success() throws Exception {
        // given
        SetNotificationDto dto = new SetNotificationDto(true, true, true, true, true);
        doNothing().when(setNotificationService).saveSetting(any(SetNotificationDto.class));

        // when
        ResultActions result = mockMvc.perform(post(API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("실패: 알림 설정 저장 중 오류")
    @WithMockUser
    void saveSettings_Error() throws Exception {
        // given
        SetNotificationDto dto = new SetNotificationDto(true, true, true, true, true);
        doThrow(new RuntimeException("DB 오류")).when(setNotificationService).saveSetting(any(SetNotificationDto.class));

        // when
        ResultActions result = mockMvc.perform(post(API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));

        // then
        result.andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("성공: 알림 설정 조회")
    @WithMockUser
    void getSettings_Success() throws Exception {
        // given
        SetNotificationDto dto = new SetNotificationDto(true, false, true, false, true);
        given(setNotificationService.getSetting()).willReturn(dto);

        // when
        ResultActions result = mockMvc.perform(get(API_PATH).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.issueCreated").value(true))
                .andExpect(jsonPath("$.issueStatus").value(false))
                .andExpect(jsonPath("$.meetingCreated").value(true))
                .andExpect(jsonPath("$.meetingStatus").value(false))
                .andExpect(jsonPath("$.commentMention").value(true));
    }
}
