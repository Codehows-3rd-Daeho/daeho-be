package com.codehows.daehobe.meeting.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.meeting.dto.MeetingDto;
import com.codehows.daehobe.meeting.dto.MeetingFormDto;
import com.codehows.daehobe.meeting.dto.MeetingListDto;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.service.MeetingService;
import com.codehows.daehobe.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(MeetingController.class)
@Import(JwtService.class)
class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MeetingService meetingService;

    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("성공: 회의 생성")
    @WithMockUser(username = "1")
    void createMeeting_Success() throws Exception {
        // given
        MeetingFormDto formDto = MeetingFormDto.builder().title("새 회의").build();
        MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(formDto));
        Meeting savedMeeting = Meeting.builder().id(1L).build();
        given(meetingService.createMeeting(any(), any(), anyString())).willReturn(savedMeeting);

        // when
        ResultActions result = mockMvc.perform(multipart("/meeting/create")
                .file(data)
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$").value(1L));
    }

    @Test
    @DisplayName("성공: 회의 상세 조회")
    @WithMockUser(username = "1")
    void getMeetingDtl_Success() throws Exception {
        // given
        Long meetingId = 1L;
        MeetingDto meetingDto = MeetingDto.builder().title("테스트 회의").build();
        given(meetingService.getMeetingDtl(anyLong(), anyLong())).willReturn(meetingDto);

        // when
        ResultActions result = mockMvc.perform(get("/meeting/{id}", meetingId).with(csrf()));

        // then
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.title").value("테스트 회의"));
    }

    @Test
    @DisplayName("성공: 회의 목록 조회")
    @WithMockUser
    void getMeetings_Success() throws Exception {
        // given
        PageImpl<MeetingListDto> meetingPage = new PageImpl<>(Collections.singletonList(new MeetingListDto()));
        given(meetingService.findAll(any(), any(Pageable.class), any())).willReturn(meetingPage);

        // when
        ResultActions result = mockMvc.perform(get("/meeting/list").with(csrf()));

        // then
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("성공: 회의 삭제")
    @WithMockUser
    void deleteMeeting_Success() throws Exception {
        // given
        Long meetingId = 1L;
        given(meetingService.deleteMeeting(meetingId)).willReturn(new Meeting());

        // when
        ResultActions result = mockMvc.perform(delete("/meeting/{id}", meetingId).with(csrf()));

        // then
        result.andExpect(status().isOk());
    }
}
