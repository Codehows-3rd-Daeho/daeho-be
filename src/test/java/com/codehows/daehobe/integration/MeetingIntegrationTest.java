package com.codehows.daehobe.integration;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.Role;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.repository.CategoryRepository;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import com.codehows.daehobe.masterData.repository.JobPositionRepository;
import com.codehows.daehobe.meeting.dto.MeetingFormDto;
import com.codehows.daehobe.meeting.dto.MeetingMemberDto;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회의 CRUD 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@ExtendWith(PerformanceLoggingExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[통합] 회의 관리 API")
class MeetingIntegrationTest {

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
    private static Long createdMeetingId;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findAll().getFirst();
        JobPosition pos = jobPositionRepository.findAll().getFirst();
        Category category = categoryRepository.findAll().getFirst();

        Member member = memberRepository.findByLoginId("meetingTestUser").orElseGet(() ->
                memberRepository.save(Member.builder()
                        .loginId("meetingTestUser")
                        .password(passwordEncoder.encode("password1234"))
                        .name("회의테스터")
                        .department(dept)
                        .jobPosition(pos)
                        .phone("010-3333-4444")
                        .email("meeting@test.com")
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
    @DisplayName("회의 생성")
    void createMeeting() throws Exception {
        MeetingFormDto formDto = MeetingFormDto.builder()
                .title("통합테스트 회의")
                .content("회의 내용입니다.")
                .status("PLANNED")
                .categoryId(categoryId)
                .issueId(0L)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(1).plusHours(2))
                .departmentIds(List.of(departmentId))
                .members(List.of(MeetingMemberDto.builder()
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

        MvcResult result = mockMvc.perform(multipart("/meeting/create")
                        .file(data)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        createdMeetingId = Long.parseLong(body.trim());
    }

    @Test
    @Order(2)
    @DisplayName("회의 상세 조회")
    void getMeetingDtl() throws Exception {
        if (createdMeetingId == null) return;

        mockMvc.perform(get("/meeting/{id}", createdMeetingId)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("통합테스트 회의"))
                .andDo(print());
    }

    @Test
    @Order(3)
    @DisplayName("회의 리스트 조회 (페이징)")
    void getMeetingList() throws Exception {
        mockMvc.perform(get("/meeting/list")
                        .header("Authorization", userToken)
                        .param("memberId", String.valueOf(memberId))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(4)
    @DisplayName("회의 캘린더 조회")
    void getMeetingScheduler() throws Exception {
        mockMvc.perform(get("/meeting/scheduler")
                        .header("Authorization", userToken)
                        .param("memberId", String.valueOf(memberId))
                        .param("year", String.valueOf(LocalDateTime.now().getYear()))
                        .param("month", String.valueOf(LocalDateTime.now().getMonthValue())))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(5)
    @DisplayName("회의 삭제 (논리 삭제)")
    void deleteMeeting() throws Exception {
        if (createdMeetingId == null) return;

        mockMvc.perform(delete("/meeting/{id}", createdMeetingId)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @Order(6)
    @DisplayName("인증 없이 회의 접근 시 401")
    void getMeeting_Unauthorized() throws Exception {
        mockMvc.perform(get("/meeting/list")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }
}
