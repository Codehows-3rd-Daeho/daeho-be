package com.codehows.daehobe.integration;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 기준 데이터(카테고리/부서/직급) CRUD 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@ExtendWith(PerformanceLoggingExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[통합] 기준 데이터 관리 API")
class MasterDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generateToken("1", "ROLE_ADMIN");
    }

    // ==================== 카테고리 ====================

    @Nested
    @DisplayName("카테고리 CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CategoryTests {

        @Test
        @Order(1)
        @DisplayName("카테고리 목록 조회 (인증 필요)")
        void getCategories() throws Exception {
            mockMvc.perform(get("/masterData/category")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andDo(print());
        }

        @Test
        @Order(2)
        @DisplayName("카테고리 생성 (관리자)")
        void createCategory() throws Exception {
            MasterDataDto dto = new MasterDataDto(null, "통합테스트카테고리");

            mockMvc.perform(post("/admin/category")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("통합테스트카테고리"))
                    .andDo(print());
        }

        @Test
        @Order(3)
        @DisplayName("카테고리 중복 생성 시 400")
        void createCategory_Duplicate() throws Exception {
            // InitialDataLoader가 생성한 "일반업무" 카테고리와 중복
            MasterDataDto dto = new MasterDataDto(null, "일반업무");

            mockMvc.perform(post("/admin/category")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }
    }

    // ==================== 부서 ====================

    @Nested
    @DisplayName("부서 CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DepartmentTests {

        @Test
        @Order(1)
        @DisplayName("부서 목록 조회")
        void getDepartments() throws Exception {
            mockMvc.perform(get("/masterData/department")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andDo(print());
        }

        @Test
        @Order(2)
        @DisplayName("부서 생성 (관리자)")
        void createDepartment() throws Exception {
            MasterDataDto dto = new MasterDataDto(null, "통합테스트부서");

            mockMvc.perform(post("/admin/department")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("통합테스트부서"))
                    .andDo(print());
        }

        @Test
        @Order(3)
        @DisplayName("부서 중복 생성 시 400")
        void createDepartment_Duplicate() throws Exception {
            MasterDataDto dto = new MasterDataDto(null, "경영");

            mockMvc.perform(post("/admin/department")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }
    }

    // ==================== 직급 ====================

    @Nested
    @DisplayName("직급 CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class JobPositionTests {

        @Test
        @Order(1)
        @DisplayName("직급 목록 조회")
        void getJobPositions() throws Exception {
            mockMvc.perform(get("/masterData/jobPosition")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andDo(print());
        }

        @Test
        @Order(2)
        @DisplayName("직급 생성 (관리자)")
        void createJobPosition() throws Exception {
            MasterDataDto dto = new MasterDataDto(null, "통합테스트직급");

            mockMvc.perform(post("/admin/jobPosition")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("통합테스트직급"))
                    .andDo(print());
        }
    }

    // ==================== 권한 테스트 ====================

    @Test
    @DisplayName("일반 유저가 관리자 API에 접근 시 403")
    void adminApi_Forbidden_ForUser() throws Exception {
        String userToken = "Bearer " + jwtService.generateToken("999", "ROLE_USER");

        mockMvc.perform(post("/admin/category")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MasterDataDto(null, "테스트"))))
                .andExpect(status().isForbidden())
                .andDo(print());
    }
}
