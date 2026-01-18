package com.codehows.daehobe.member.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.member.dto.MemberDto;
import com.codehows.daehobe.member.dto.MemberListDto;
import com.codehows.daehobe.member.dto.MemberProfileDto;
import com.codehows.daehobe.member.dto.PasswordRequestDto;
import com.codehows.daehobe.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import(JwtService.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    // Helper method to create a valid MemberDto
    private MemberDto createValidMemberDto() {
        return MemberDto.builder()
                .loginId("testUser123")
                .password("password123")
                .name("테스터")
                .departmentId(1L)
                .jobPositionId(1L)
                .phone("010-1234-5678")
                .email("test@example.com")
                .isEmployed(true)
                .role("USER")
                .build();
    }

    private PasswordRequestDto createPasswordRequestDto(String newPassword) {
        return new PasswordRequestDto(newPassword);
    }


    @Nested
    @DisplayName("회원 목록 조회 - GET /admin/member")
    class GetMembers {
        private final String API_PATH = "/admin/member";

        @Test
        @DisplayName("성공: 회원 목록 조회 (키워드 없음, 페이지네이션 기본값)")
        @WithMockUser(roles = "ADMIN")
        void getMembers_Success_NoKeyword() throws Exception {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            MemberListDto member1 = MemberListDto.builder()
                    .id(1L).name("홍길동").departmentName("개발").jobPositionName("사원")
                    .phone("010-1111-2222").email("hong@test.com").isEmployed(true).isAdmin(false)
                    .build();
            PageImpl<MemberListDto> memberPage = new PageImpl<>(Collections.singletonList(member1), pageable, 1);
            given(memberService.findAll(any(Pageable.class), isNull())).willReturn(memberPage);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("홍길동"));
        }

        @Test
        @DisplayName("성공: 회원 목록 조회 (키워드 포함)")
        @WithMockUser(roles = "ADMIN")
        void getMembers_Success_WithKeyword() throws Exception {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            MemberListDto member1 = MemberListDto.builder()
                    .id(1L).name("홍길동").departmentName("개발").jobPositionName("사원")
                    .phone("010-1111-2222").email("hong@test.com").isEmployed(true).isAdmin(false)
                    .build();
            PageImpl<MemberListDto> memberPage = new PageImpl<>(Collections.singletonList(member1), pageable, 1);
            given(memberService.findAll(any(Pageable.class), eq("홍길동"))).willReturn(memberPage);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH)
                    .param("keyword", "홍길동")
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("홍길동"));
        }

        @Test
        @DisplayName("실패: 회원 목록 조회 중 내부 서버 오류")
        @WithMockUser(roles = "ADMIN")
        void getMembers_InternalServerError() throws Exception {
            // given
            doThrow(new RuntimeException("서비스 오류")).when(memberService).findAll(any(Pageable.class), anyString());

            // when
            ResultActions result = mockMvc.perform(get(API_PATH)
                    .param("keyword", "test")  // 또는 .param("keyword", null)
                    .param("page", "0")
                    .param("size", "10")
                    .with(csrf()));

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("회원 조회 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("회원 상세 조회 - GET /admin/member/{id}")
    class GetMemberDtl {
        private final String API_PATH = "/admin/member/{id}";
        private final Long MEMBER_ID = 1L;

        @Test
        @DisplayName("성공: 회원 상세 조회")
        @WithMockUser(roles = "ADMIN")
        void getMemberDtl_Success() throws Exception {
            // given
            MemberDto memberDto = createValidMemberDto();
            given(memberService.getMemberDtl(MEMBER_ID)).willReturn(memberDto);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value(memberDto.getLoginId()));
        }

        @Test
        @DisplayName("실패: 회원 상세 조회 중 내부 서버 오류")
        @WithMockUser(roles = "ADMIN")
        void getMemberDtl_InternalServerError() throws Exception {
            // given
            doThrow(new RuntimeException("서비스 오류")).when(memberService).getMemberDtl(MEMBER_ID);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("회원 조회 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("비밀번호 초기화 및 임시 비밀번호 생성 - POST /admin/member/{id}/generatePwd")
    class GeneratePwd {
        private final String API_PATH = "/admin/member/{id}/generatePwd";
        private final Long MEMBER_ID = 1L;

        @Test
        @DisplayName("성공: 비밀번호 초기화 및 임시 비밀번호 생성")
        @WithMockUser(roles = "ADMIN")
        void generatePwd_Success() throws Exception {
            // given
            given(memberService.generatePwd(MEMBER_ID)).willReturn("tempPassword123");

            // when
            ResultActions result = mockMvc.perform(post(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("비밀번호가 성공적으로 초기화되었습니다."))
                    .andExpect(jsonPath("$.newPassword").value("tempPassword123"));
        }

        @Test
        @DisplayName("실패: 비밀번호 초기화 중 내부 서버 오류")
        @WithMockUser(roles = "ADMIN")
        void generatePwd_InternalServerError() throws Exception {
            // given
            doThrow(new RuntimeException("서비스 오류")).when(memberService).generatePwd(MEMBER_ID);

            // when
            ResultActions result = mockMvc.perform(post(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("비밀번호 초기화 중 오류가 발생했습니다."));
        }
    }

    @Nested
    @DisplayName("회원 정보 업데이트 - PUT /admin/member/{id}")
    class UpdateMember {
        private final String API_PATH = "/admin/member/{id}";
        private final Long MEMBER_ID = 1L;

        @Test
        @DisplayName("성공: 회원 정보 업데이트 (파일 없음)")
        @WithMockUser(roles = "ADMIN")
        void updateMember_Success_NoFile() throws Exception {
            // given
            MemberDto updateDto = createValidMemberDto();
            doNothing().when(memberService).updateMember(eq(MEMBER_ID), any(MemberDto.class), anyList(), anyList());

            // when
            ResultActions result = mockMvc.perform(multipart(API_PATH, MEMBER_ID)
                    .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(updateDto)))
                    .with(request -> {
                        request.setMethod("PUT"); // multipart requests default to POST, change to PUT
                        return request;
                    })
                    .with(csrf())
            );

            // then
            result.andExpect(status().isOk());
        }

        @Test
        @DisplayName("성공: 회원 정보 업데이트 (파일 포함 및 삭제)")
        @WithMockUser(roles = "ADMIN")
        void updateMember_Success_WithFileAndRemoval() throws Exception {
            // given
            MemberDto updateDto = createValidMemberDto();
            MockMultipartFile profileImage = new MockMultipartFile("file", "profile.jpg", MediaType.IMAGE_JPEG_VALUE, "some image".getBytes());
            List<Long> removeFileIds = Collections.singletonList(10L);
            String removeFileIdsJson = objectMapper.writeValueAsString(removeFileIds);

            doNothing().when(memberService).updateMember(eq(MEMBER_ID), any(MemberDto.class), anyList(), anyList());

            // when
            ResultActions result = mockMvc.perform(multipart(API_PATH, MEMBER_ID)
                    .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(updateDto)))
                    .file(profileImage)
                    .file(new MockMultipartFile("removeFileIds", "", MediaType.APPLICATION_JSON_VALUE, removeFileIdsJson.getBytes()))
                    .with(request -> {
                        request.setMethod("PUT");
                        return request;
                    })
                    .with(csrf())
            );

            // then
            result.andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 회원 정보 업데이트 중 내부 서버 오류")
        @WithMockUser(roles = "ADMIN")
        void updateMember_InternalServerError() throws Exception {
            // given
            MemberDto updateDto = createValidMemberDto();
            doThrow(new RuntimeException("서비스 오류")).when(memberService).updateMember(eq(MEMBER_ID), any(MemberDto.class), anyList(), anyList());

            // when
            ResultActions result = mockMvc.perform(multipart(API_PATH, MEMBER_ID)
                    .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(updateDto)))
                    .with(request -> {
                        request.setMethod("PUT");
                        return request;
                    })
                    .with(csrf())
            );

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("회원 수정 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("마이페이지 프로필 조회 - GET /mypage/{id}")
    class GetMyPageProfile {
        private final String API_PATH = "/mypage/{id}";
        private final Long MEMBER_ID = 1L;

        @Test
        @DisplayName("성공: 마이페이지 프로필 조회")
        @WithMockUser
        void getMemberProfile_Success() throws Exception {
            // given
            MemberProfileDto profileDto = MemberProfileDto.builder()
                    .name("테스터")
                    .email("test@example.com")
                    .build();
            given(memberService.getMemberProfile(MEMBER_ID)).willReturn(profileDto);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("테스터"));
        }

        @Test
        @DisplayName("실패: 마이페이지 프로필 조회 중 내부 서버 오류")
        @WithMockUser
        void getMemberProfile_InternalServerError() throws Exception {
            // given
            doThrow(new RuntimeException("서비스 오류")).when(memberService).getMemberProfile(MEMBER_ID);

            // when
            ResultActions result = mockMvc.perform(get(API_PATH, MEMBER_ID).with(csrf()));

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("회원 조회 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정 - PATCH /mypage/password")
    class ChangePassword {
        private final String API_PATH = "/mypage/password";

        @Test
        @DisplayName("성공: 비밀번호 재설정")
        @WithMockUser
        void changePassword_Success() throws Exception {
            // given
            PasswordRequestDto passwordRequestDto = createPasswordRequestDto("newPassword123");
            doNothing().when(memberService).changPwd(anyString());

            // when
            ResultActions result = mockMvc.perform(patch(API_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequestDto))
                    .with(csrf()));

            // then
            result.andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 비밀번호 재설정 중 내부 서버 오류")
        @WithMockUser
        void changePassword_InternalServerError() throws Exception {
            // given
            PasswordRequestDto passwordRequestDto = createPasswordRequestDto("newPassword123");
            doThrow(new RuntimeException("서비스 오류")).when(memberService).changPwd(anyString());

            // when
            ResultActions result = mockMvc.perform(patch(API_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequestDto))
                    .with(csrf()));

            // then
            result.andExpect(status().isInternalServerError());
        }
    }
}
