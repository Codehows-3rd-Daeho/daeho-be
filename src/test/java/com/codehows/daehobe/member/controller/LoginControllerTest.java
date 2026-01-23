package com.codehows.daehobe.member.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.member.dto.LoginDto;
import com.codehows.daehobe.member.dto.LoginResponseDto;
import com.codehows.daehobe.member.service.LoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
////import org.springframework.security.test.context.support.WithMockUser; // WithMockUser는 더 이상 사용하지 않으므로 제거

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
////import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // csrf는 더 이상 사용하지 않으므로 제거
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(JwtService.class)
@AutoConfigureMockMvc(addFilters = false) // Spring Security 필터 비활성화
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LoginService loginService;

    private LoginDto createLoginDto(String loginId, String password) {
        return new LoginDto(loginId, password);
    }

    @Test
    @DisplayName("성공: 로그인 요청")

    void login_Success() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("testUser", "password");
        LoginResponseDto loginResponseDto = LoginResponseDto.builder()
                .token("Bearer test-jwt-token")
                .memberId(1L)
                .name("테스터")
                .jobPosition("개발자")
                .profileUrl("")
                .role("USER")
                .build();
        given(loginService.login(any(LoginDto.class))).willReturn(loginResponseDto);

        // when
        ResultActions result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("Bearer test-jwt-token"))
                .andExpect(jsonPath("$.memberId").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("실패: 로그인 요청 (유효성 검증 실패 - 빈 아이디)")

    void login_ValidationFailure_BlankLoginId() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("", "password"); // 빈 아이디

        // when
        ResultActions result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("아이디는 필수 입력값입니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("실패: 로그인 요청 (유효성 검증 실패 - 짧은 비밀번호)")

    void login_ValidationFailure_ShortPassword() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("testUser", "short"); // 8자 미만 비밀번호

        // when
        ResultActions result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("비밀번호는 최소 8자 이상이어야 합니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("실패: 로그인 요청 (서비스 예외 - 잘못된 자격 증명)")

    void login_ServiceException_BadCredentials() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("testUser", "wrongPassword");
        given(loginService.login(any(LoginDto.class))).willThrow(new BadCredentialsException("자격 증명 실패"));

        // when
        ResultActions result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)));

        // then
        result.andExpect(status().isInternalServerError())
                .andDo(print()); // 컨트롤러에서 잡지 않고 Spring이 500으로 처리
    }
}
