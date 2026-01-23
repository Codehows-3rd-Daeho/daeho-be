package com.codehows.daehobe.logging.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.logging.dto.LogDto;
import com.codehows.daehobe.logging.service.LogService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogController.class)
@Import(JwtService.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogService logService;

    @Test
    @DisplayName("성공: 이슈 로그 조회")
    @WithMockUser
    void issueLog_Success() throws Exception {
        // given
        Long issueId = 1L;
        PageImpl<LogDto> logPage = new PageImpl<>(Collections.singletonList(LogDto.builder().message("이슈 로그").build()));
        given(logService.getIssueLogs(anyLong(), any(Pageable.class))).willReturn(logPage);

        // when
        ResultActions result = mockMvc.perform(get("/issue/{id}/log", issueId).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("이슈 로그"));
    }

    @Test
    @DisplayName("성공: 회의 로그 조회")
    @WithMockUser
    void meetingLog_Success() throws Exception {
        // given
        Long meetingId = 1L;
        PageImpl<LogDto> logPage = new PageImpl<>(Collections.singletonList(LogDto.builder().message("회의 로그").build()));
        given(logService.getMeetingLogs(anyLong(), any(Pageable.class))).willReturn(logPage);

        // when
        ResultActions result = mockMvc.perform(get("/meeting/{id}/log", meetingId).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("회의 로그"));
    }

    @Test
    @DisplayName("성공: 전체 로그 조회")
    @WithMockUser
    void allLog_Success() throws Exception {
        // given
        PageImpl<LogDto> logPage = new PageImpl<>(Collections.singletonList(LogDto.builder().message("전체 로그").build()));
        given(logService.getAllLogs(any(), any(Pageable.class))).willReturn(logPage);

        // when
        ResultActions result = mockMvc.perform(get("/admin/log").with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("전체 로그"));
    }
}
