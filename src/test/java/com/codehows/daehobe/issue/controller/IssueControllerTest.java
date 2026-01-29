package com.codehows.daehobe.issue.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.issue.dto.IssueDto;
import com.codehows.daehobe.issue.dto.IssueFormDto;
import com.codehows.daehobe.issue.dto.IssueListDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.service.IssueService;
import com.codehows.daehobe.meeting.service.MeetingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(IssueController.class)
@Import(JwtService.class)
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IssueService issueService;

    @MockitoBean
    private MeetingService meetingService;

    @Nested
    @DisplayName("이슈 생성 - POST /issue/create")
    class CreateIssue {
        private final String API_PATH = "/issue/create";

        @Test
        @DisplayName("성공: 이슈 생성")
        @WithMockUser(username = "1")
        void createIssue_Success() throws Exception {
            // given
            IssueFormDto formDto = IssueFormDto.builder()
                    .title("새 이슈")
                    .content("새 내용")
                    .build();
            MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(formDto));
            Issue savedIssue = Issue.builder().id(1L).build();
            given(issueService.createIssue(any(IssueFormDto.class), nullable(List.class), anyString())).willReturn(savedIssue);

            // when
            ResultActions result = mockMvc.perform(multipart(API_PATH)
                    .file(data)
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                  .andExpect(jsonPath("$").value(1L))
                  .andDo(print());
        }
    }

    @Nested
    @DisplayName("이슈 상세 조회 - GET /issue/{id}")
    class GetIssueDtl {
        private final String API_PATH = "/issue/{id}";

        @Test
        @DisplayName("성공: 이슈 상세 조회")
        @WithMockUser(username = "1")
        void getIssueDtl_Success() throws Exception {
            // given
            Long issueId = 1L;
            IssueDto issueDto = IssueDto.builder().title("테스트 이슈").build();
            given(issueService.getIssueDtl(anyLong(), anyLong())).willReturn(issueDto);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH, issueId).with(csrf()));

            // then
            result.andExpect(status().isOk())
                  .andExpect(jsonPath("$.title").value("테스트 이슈"));
        }
    }

    @Nested
    @DisplayName("이슈 수정 - PUT /issue/{id}")
    class UpdateIssue {
        private final String API_PATH = "/issue/{id}";

        @Test
        @DisplayName("성공: 이슈 수정")
        @WithMockUser(username = "1")
        void updateIssue_Success() throws Exception {
            // given
            Long issueId = 1L;
            IssueFormDto formDto = IssueFormDto.builder().title("수정된 이슈").build();
            MockMultipartFile data = new MockMultipartFile("data", "", "application/json", objectMapper.writeValueAsString(formDto).getBytes(StandardCharsets.UTF_8));
            given(issueService.updateIssue(anyLong(), any(IssueFormDto.class), anyList(), anyList(), anyString())).willReturn(new Issue());

            // when
            ResultActions result = mockMvc.perform(multipart(API_PATH, issueId)
                    .file(data)
                    .with(request -> {
                        request.setMethod("PUT");
                        return request;
                    })
                    .with(csrf()));

            // then
            result.andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("이슈 삭제 - DELETE /issue/{id}")
    class DeleteIssue {
        private final String API_PATH = "/issue/{id}";

        @Test
        @DisplayName("성공: 이슈 삭제")
        @WithMockUser
        void deleteIssue_Success() throws Exception {
            // given
            Long issueId = 1L;
            given(issueService.deleteIssue(issueId)).willReturn(new Issue());

            // when
            ResultActions result = mockMvc.perform(delete(API_PATH, issueId).with(csrf()));

            // then
            result.andExpect(status().isOk());
        }
    }
}
