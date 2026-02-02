package com.codehows.daehobe.integration;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.member.dto.LoginDto;
import com.codehows.daehobe.member.dto.MemberDto;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import com.codehows.daehobe.masterData.repository.JobPositionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원 통합 테스트 - 회원가입 → 로그인 → 프로필 조회 플로우
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@ExtendWith(PerformanceLoggingExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[통합] 회원 관리 API")
class MemberIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private JobPositionRepository jobPositionRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // InitialDataLoader가 기초 데이터를 생성하므로 부서/직급은 이미 존재
        adminToken = "Bearer " + jwtService.generateToken("1", "ROLE_ADMIN");
    }

    @Test
    @Order(1)
    @DisplayName("회원가입 성공")
    void signUp_Success() throws Exception {
        Long deptId = departmentRepository.findAll().getFirst().getId();
        Long jobPosId = jobPositionRepository.findAll().getFirst().getId();

        MemberDto memberDto = MemberDto.builder()
                .loginId("testuser1")
                .password("password1234")
                .name("테스트유저")
                .departmentId(deptId)
                .jobPositionId(jobPosId)
                .phone("010-1234-5678")
                .email("test@example.com")
                .isEmployed(true)
                .role("USER")
                .build();

        MockMultipartFile data = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(memberDto));

        mockMvc.perform(multipart("/signup")
                        .file(data)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andDo(print());

        assertThat(memberRepository.existsByLoginId("testuser1")).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("아이디 중복 확인")
    void checkLoginId() throws Exception {
        // 존재하지 않는 아이디
        mockMvc.perform(get("/signup/check_loginId")
                        .param("loginId", "nonexistent999")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andDo(print());
    }

    @Test
    @Order(3)
    @DisplayName("로그인 성공 - JWT 토큰 발급")
    void login_Success() throws Exception {
        // 먼저 회원가입
        Long deptId = departmentRepository.findAll().getFirst().getId();
        Long jobPosId = jobPositionRepository.findAll().getFirst().getId();

        MemberDto memberDto = MemberDto.builder()
                .loginId("logintest")
                .password("password1234")
                .name("로그인테스트")
                .departmentId(deptId)
                .jobPositionId(jobPosId)
                .phone("010-0000-0000")
                .email("login@test.com")
                .isEmployed(true)
                .role("USER")
                .build();

        MockMultipartFile data = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(memberDto));

        mockMvc.perform(multipart("/signup").file(data)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // 로그인
        LoginDto loginDto = new LoginDto("logintest", "password1234");

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.memberId").exists())
                .andExpect(jsonPath("$.name").value("로그인테스트"))
                .andDo(print())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("Bearer ");
    }

    @Test
    @Order(4)
    @DisplayName("로그인 실패 - 유효성 검증")
    void login_ValidationFail() throws Exception {
        // 비밀번호 8자 미만
        LoginDto loginDto = new LoginDto("testid", "short");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @Order(5)
    @DisplayName("관리자 - 회원 목록 조회")
    void getMembers_AsAdmin() throws Exception {
        mockMvc.perform(get("/admin/member")
                        .header("Authorization", adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(6)
    @DisplayName("인증 없이 관리자 API 접근 시 401")
    void getMembers_Unauthorized() throws Exception {
        mockMvc.perform(get("/admin/member"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @Order(7)
    @DisplayName("일반 유저로 관리자 API 접근 시 403")
    void getMembers_Forbidden() throws Exception {
        String userToken = "Bearer " + jwtService.generateToken("999", "ROLE_USER");

        mockMvc.perform(get("/admin/member")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andDo(print());
    }
}
