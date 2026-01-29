package com.codehows.daehobe.comment.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.comment.dto.CommentDto;
import com.codehows.daehobe.comment.dto.CommentRequest;
import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.comment.service.CommentService;
import com.codehows.daehobe.config.jwtAuth.JwtService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(CommentController.class)
@Import(JwtService.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    private final Long TEST_TARGET_ID = 1L; // Issue or Meeting ID
    private final Long TEST_COMMENT_ID = 1L;
    private final Long TEST_MEMBER_ID = 1L;

    private CommentRequest createCommentRequest(String content) {
        return new CommentRequest(
                TEST_TARGET_ID,
                null, // TargetType will be set by the controller based on endpoint
                content,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    @Nested
    @DisplayName("이슈 댓글 - /issue/{id}/comments")
    class IssueComments {
        private final String BASE_URL = "/issue/{id}/comments";
        private final String POST_URL = "/issue/{id}/comment";

        @Test
        @DisplayName("성공: 이슈 댓글 목록 조회")
        @WithMockUser(username = "1")
        void getCommentsByIssueId_Success() throws Exception {
            // given
            CommentDto commentDto = CommentDto.builder().content("테스트 댓글").build();
            PageImpl<CommentDto> page = new PageImpl<>(Collections.singletonList(commentDto));
            given(commentService.getCommentsByIssueId(anyLong(), any(Pageable.class))).willReturn(page);

            // when
            ResultActions result = mockMvc.perform(get(BASE_URL, TEST_TARGET_ID)
                    .param("page", "0")
                    .param("size", "10")
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].content").value("테스트 댓글"))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공: 이슈 댓글 작성")
        @WithMockUser(username = "1")
        void createIssueComment_Success() throws Exception {
            // given
            CommentRequest request = createCommentRequest("새 이슈 댓글");
            MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
            Comment savedComment = Comment.builder().id(TEST_COMMENT_ID).content("새 이슈 댓글").build();
            given(commentService.createIssueComment(anyLong(), any(CommentRequest.class), anyLong(), any())).willReturn(savedComment);

            // when
            ResultActions result = mockMvc.perform(multipart(POST_URL, TEST_TARGET_ID)
                    .file(data)
                    .with(request1 -> {
                        request1.setMethod("POST");
                        return request1;
                    })
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TEST_COMMENT_ID))
                    .andExpect(jsonPath("$.content").value("새 이슈 댓글"))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("회의 댓글 - /meeting/{id}/comments")
    class MeetingComments {
        private final String BASE_URL = "/meeting/{id}/comments";
        private final String POST_URL = "/meeting/{id}/comment";

        @Test
        @DisplayName("성공: 회의 댓글 목록 조회")
        @WithMockUser(username = "1")
        void getCommentsByMeetingId_Success() throws Exception {
            // given
            CommentDto commentDto = CommentDto.builder().content("테스트 회의 댓글").build();
            PageImpl<CommentDto> page = new PageImpl<>(Collections.singletonList(commentDto));
            given(commentService.getCommentsByMeetingId(anyLong(), any(Pageable.class))).willReturn(page);

            // when
            ResultActions result = mockMvc.perform(get(BASE_URL, TEST_TARGET_ID)
                    .param("page", "0")
                    .param("size", "10")
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].content").value("테스트 회의 댓글"))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공: 회의 댓글 작성")
        @WithMockUser(username = "1")
        void createMeetingComment_Success() throws Exception {
            // given
            CommentRequest request = createCommentRequest("새 회의 댓글");
            MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
            Comment savedComment = Comment.builder().id(TEST_COMMENT_ID).content("새 회의 댓글").build();
            given(commentService.createMeetingComment(anyLong(), any(CommentRequest.class), anyLong(), any())).willReturn(savedComment);

            // when
            ResultActions result = mockMvc.perform(multipart(POST_URL, TEST_TARGET_ID)
                    .file(data)
                    .with(request1 -> {
                        request1.setMethod("POST");
                        return request1;
                    })
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TEST_COMMENT_ID))
                    .andExpect(jsonPath("$.content").value("새 회의 댓글"))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("공통 댓글 기능 - /comment/{id}")
    class CommonCommentFunctions {
        private final String BASE_URL = "/comment/{id}";

        @Test
        @DisplayName("성공: 댓글 수정")
        @WithMockUser(username = "1")
        void updateComment_Success() throws Exception {
            // given
            CommentRequest request = createCommentRequest("수정된 댓글");
            MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
            Comment updatedComment = Comment.builder().id(TEST_COMMENT_ID).content("수정된 댓글").build();
            given(commentService.updateComment(anyLong(), any(CommentRequest.class), any(), any())).willReturn(updatedComment);

            // when
            ResultActions result = mockMvc.perform(multipart(BASE_URL, TEST_COMMENT_ID)
                    .file(data)
                    .with(request1 -> {
                        request1.setMethod("PUT");
                        return request1;
                    })
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andDo(print());
        }

        @Test
        @DisplayName("성공: 댓글 삭제")
        @WithMockUser
        void deleteComment_Success() throws Exception {
            // given
            Comment deletedComment = Comment.builder().id(TEST_COMMENT_ID).build();
            given(commentService.deleteComment(anyLong())).willReturn(deletedComment);

            // when
            ResultActions result = mockMvc.perform(delete(BASE_URL, TEST_COMMENT_ID).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andDo(print());
        }
    }
}