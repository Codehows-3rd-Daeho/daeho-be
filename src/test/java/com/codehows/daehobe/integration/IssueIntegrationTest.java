package com.codehows.daehobe.integration;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.Role;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.issue.dto.IssueFormDto;
import com.codehows.daehobe.issue.dto.IssueMemberDto;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.repository.CategoryRepository;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import com.codehows.daehobe.masterData.repository.JobPositionRepository;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 이슈 CRUD 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@ExtendWith(PerformanceLoggingExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[통합] 이슈 관리 API")
class IssueIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private JobPositionRepository jobPositionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;
    private Long memberId;
    private Long categoryId;
    private Long departmentId;
    private static Long createdIssueId;

    @BeforeEach
    void setUp() {
        // 테스트용 회원이 없으면 생성
        Department dept = departmentRepository.findAll().getFirst();
        JobPosition pos = jobPositionRepository.findAll().getFirst();
        Category category = categoryRepository.findAll().getFirst();

        Member member = memberRepository.findByLoginId("issueTestUser").orElseGet(() ->
                memberRepository.save(Member.builder()
                        .loginId("issueTestUser")
                        .password(passwordEncoder.encode("password1234"))
                        .name("이슈테스터")
                        .department(dept)
                        .jobPosition(pos)
                        .phone("010-1111-2222")
                        .email("issue@test.com")
                        .isEmployed(true)
                        .role(Role.USER)
                        .build()));

        memberId = member.getId();
        categoryId = category.getId();
        departmentId = dept.getId();
        userToken = "Bearer " + jwtService.generateToken(String.valueOf(memberId), "ROLE_USER");
    }

    @Test
    @Order(1)
    @DisplayName("이슈 생성")
    void createIssue() throws Exception {
        IssueFormDto formDto = IssueFormDto.builder()
                .title("통합테스트 이슈")
                .content("이슈 내용입니다.")
                .status("PLANNED")
                .categoryId(categoryId)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .departmentIds(List.of(departmentId))
                .members(List.of(IssueMemberDto.builder()
                        .id(memberId)
                        .host(true)
                        .permitted(true)
                        .build()))
                .isDel(false)
                .isPrivate(false)
                .build();

        MockMultipartFile data = new MockMultipartFile(
                "data", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(formDto));

        MvcResult result = mockMvc.perform(multipart("/issue/create")
                        .file(data)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        createdIssueId = Long.parseLong(body.trim());
    }

    @Test
    @Order(2)
    @DisplayName("이슈 상세 조회")
    void getIssueDtl() throws Exception {
        if (createdIssueId == null) return;

        mockMvc.perform(get("/issue/{id}", createdIssueId)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("통합테스트 이슈"))
                .andDo(print());
    }

    @Test
    @Order(3)
    @DisplayName("이슈 칸반 데이터 조회")
    void getKanbanData() throws Exception {
        mockMvc.perform(get("/issue/kanban")
                        .header("Authorization", userToken)
                        .param("memberId", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(4)
    @DisplayName("이슈 리스트 조회 (페이징)")
    void getIssueList() throws Exception {
        mockMvc.perform(get("/issue/list")
                        .header("Authorization", userToken)
                        .param("memberId", String.valueOf(memberId))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(5)
    @DisplayName("이슈 삭제 (논리 삭제)")
    void deleteIssue() throws Exception {
        if (createdIssueId == null) return;

        mockMvc.perform(delete("/issue/{id}", createdIssueId)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(6)
    @DisplayName("인증 없이 이슈 접근 시 401")
    void getIssue_Unauthorized() throws Exception {
        mockMvc.perform(get("/issue/list")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }
}
