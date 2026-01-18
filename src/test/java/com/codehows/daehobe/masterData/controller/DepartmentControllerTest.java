package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.config.SpringSecurity.SecurityConfig;
import com.codehows.daehobe.config.jpaAuditor.AuditorAwareImpl;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.service.DepartmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

/**
 * <p>DepartmentController에 대한 웹 계층 통합 테스트 클래스입니다.</p>
 *
 * <p>{@code @WebMvcTest} 어노테이션을 사용하여 웹 계층만 로드하고,
 * {@code @MockBean}을 통해 `DepartmentService`를 Mock 객체로 주입하여
 * 컨트롤러의 요청 처리, 응답 포맷, 예외 처리 등을 격리하여 테스트합니다.</p>
 *
 * <p>관리자 권한이 필요한 API는 실제 Security 필터를 거치지 않으므로,
 * Spring Security 테스트 유틸리티를 사용하여 인증된 사용자(ADMIN 역할)로
 * 가장하여 테스트를 수행합니다.</p>
 */
@WebMvcTest(value = DepartmentController.class)
@Import({ JwtService.class })
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc; // HTTP 요청을 시뮬레이션

    @Autowired
    private ObjectMapper objectMapper; // 객체를 JSON으로 변환

    @MockitoBean
    private DepartmentService departmentService; // 컨트롤러가 의존하는 서비스를 MockBean으로 주입

    private final String ADMIN_API_PATH_PREFIX = "/admin/department";
    private final String PUBLIC_API_PATH_PREFIX = "/masterData/department";

    /**
     * <p>모든 부서 조회 요청 성공 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.findAll()`이 두 개의 `MasterDataDto` 리스트를 반환하도록 Mocking.</li>
     *     <li>`GET /masterData/department` 요청 수행.</li>
     *     <li>HTTP 200 OK 상태 코드와 함께 예상하는 JSON 응답 본문 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 모든 부서 조회")
    @WithMockUser
    void getDpt_Success() throws Exception {
        // given
        List<MasterDataDto> departments = Arrays.asList(
                new MasterDataDto(1L, "부서1"),
                new MasterDataDto(2L, "부서2")
        );
        given(departmentService.findAll()).willReturn(departments);

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("부서1"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("부서2"))
                .andDo(print());
    }

    /**
     * <p>부서가 없을 때 모든 부서 조회 요청 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.findAll()`이 빈 리스트를 반환하도록 Mocking.</li>
     *     <li>`GET /masterData/department` 요청 수행.</li>
     *     <li>HTTP 200 OK 상태 코드와 함께 빈 JSON 배열 응답 본문 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 없음 시 모든 부서 조회")
    @WithMockUser
    void getDpt_EmptyList() throws Exception {
        // given
        given(departmentService.findAll()).willReturn(Collections.emptyList());

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty())
                .andDo(print());
    }

    /**
     * <p>부서 조회 중 서비스 내부 오류 발생 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.findAll()` 호출 시 `RuntimeException`을 발생시키도록 Mocking.</li>
     *     <li>`GET /masterData/department` 요청 수행.</li>
     *     <li>HTTP 500 Internal Server Error 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 조회 중 내부 서버 오류")
    @WithMockUser
    void getDpt_InternalServerError() throws Exception {
        // given
        given(departmentService.findAll()).willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("부서 조회 중 오류 발생"))
                .andDo(print());
    }

    /**
     * <p>새로운 부서 생성 요청 성공 테스트 (관리자 권한). For this project, a simple POST request
     * to admin path is enough without specific Spring Security setup, as it's not a full integration test.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`MasterDataDto` 객체 생성.</li>
     *     <li>`departmentService.createDpt()`가 저장된 `Department` 객체를 반환하도록 Mocking.</li>
     *     <li>`POST /admin/department` 요청 수행.</li>
     *     <li>HTTP 200 OK 상태 코드와 함께 예상하는 JSON 응답 본문 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 생성 (관리자)")
    @WithMockUser
    void createDpt_Success() throws Exception {
        // given
        MasterDataDto createDto = new MasterDataDto(null, "새로운부서");
        Department createdDepartment = Department.builder().id(3L).name("새로운부서").build();
        given(departmentService.createDpt(any(MasterDataDto.class))).willReturn(createdDepartment);

        // when
        ResultActions resultActions = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.name").value("새로운부서"))
                .andDo(print());
    }

    /**
     * <p>중복된 이름으로 부서 생성 요청 시 `IllegalArgumentException` 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.createDpt()` 호출 시 `IllegalArgumentException`을 발생시키도록 Mocking.</li>
     *     <li>`POST /admin/department` 요청 수행.</li>
     *     <li>HTTP 400 Bad Request 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 중복 이름으로 부서 생성 시 Bad Request (관리자)")
    @WithMockUser
    void createDpt_DuplicateName_BadRequest() throws Exception {
        // given
        MasterDataDto createDto = new MasterDataDto(null, "중복부서");
        given(departmentService.createDpt(any(MasterDataDto.class)))
                .willThrow(new IllegalArgumentException("이미 존재하는 부서입니다: 중복부서"));

        // when
        ResultActions resultActions = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("이미 존재하는 부서입니다: 중복부서"))
                .andDo(print());
    }

    /**
     * <p>부서 생성 중 서비스 내부 오류 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.createDpt()` 호출 시 `RuntimeException`을 발생시키도록 Mocking.</li>
     *     <li>`POST /admin/department` 요청 수행.</li>
     *     <li>HTTP 500 Internal Server Error 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 생성 중 내부 서버 오류 (관리자)")
    @WithMockUser
    void createDpt_InternalServerError() throws Exception {
        // given
        MasterDataDto createDto = new MasterDataDto(null, "새로운부서");
        given(departmentService.createDpt(any(MasterDataDto.class)))
                .willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions resultActions = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("부서 등록 중 오류 발생"))
                .andDo(print());
    }

    /**
     * <p>부서 삭제 요청 성공 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.deleteDpt()`가 성공적으로 완료되도록 Mocking.</li>
     *     <li>`DELETE /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 204 No Content 상태 코드 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 삭제 (관리자)")
    @WithMockUser
    void deleteDpt_Success() throws Exception {
        // given
        Long departmentId = 1L;
        doNothing().when(departmentService).deleteDpt(departmentId);

        // when
        ResultActions resultActions = mockMvc.perform(
                delete(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    /**
     * <p>삭제할 부서를 찾을 수 없을 때 `EntityNotFoundException` 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.deleteDpt()` 호출 시 `EntityNotFoundException`을 발생시키도록 Mocking.</li>
     *     <li>`DELETE /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 404 Not Found 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 삭제할 부서 없음 시 Not Found (관리자)")
    @WithMockUser
    void deleteDpt_NotFound() throws Exception {
        // given
        Long departmentId = 99L;
        doThrow(new EntityNotFoundException()).when(departmentService).deleteDpt(departmentId);

        // when
        ResultActions resultActions = mockMvc.perform(
                delete(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("삭제하려는 부서를 찾을 수 없습니다."))
                .andDo(print());
    }

    /**
     * <p>부서 삭제 중 서비스 내부 오류 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.deleteDpt()` 호출 시 `RuntimeException`을 발생시키도록 Mocking.</li>
     *     <li>`DELETE /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 500 Internal Server Error 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 삭제 중 내부 서버 오류 (관리자)")
    @WithMockUser
    void deleteDpt_InternalServerError() throws Exception {
        // given
        Long departmentId = 1L;
        doThrow(new RuntimeException("서비스 오류")).when(departmentService).deleteDpt(departmentId);

        // when
        ResultActions resultActions = mockMvc.perform(
                delete(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("부서 삭제 중 알 수 없는 오류 발생"))
                .andDo(print());
    }

    /**
     * <p>부서 이름 업데이트 요청 성공 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`MasterDataDto` 객체 생성.</li>
     *     <li>`departmentService.updateDpt()`가 업데이트된 `Department` 객체를 반환하도록 Mocking.</li>
     *     <li>`PATCH /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 200 OK 상태 코드와 함께 예상하는 JSON 응답 본문 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 이름 업데이트 (관리자)")
    @WithMockUser
    void updateDpt_Success() throws Exception {
        // given
        Long departmentId = 1L;
        String updatedName = "업데이트된부서";
        MasterDataDto updateDto = new MasterDataDto(departmentId, updatedName);
        Department updatedDepartment = Department.builder().id(departmentId).name(updatedName).build();

        given(departmentService.updateDpt(anyLong(), any(MasterDataDto.class))).willReturn(updatedDepartment);

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(departmentId))
                .andExpect(jsonPath("$.name").value(updatedName))
                .andDo(print());
    }

    /**
     * <p>업데이트할 부서를 찾을 수 없을 때 `EntityNotFoundException` 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.updateDpt()` 호출 시 `EntityNotFoundException`을 발생시키도록 Mocking.</li>
     *     <li>`PATCH /admin/department/{id}` 요청 수행.</li>
     *     <li>Controller의 일반 `Exception` 핸들러에 의해 HTTP 500 Internal Server Error 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 업데이트할 부서 없음 시 Internal Server Error (관리자)")
    @WithMockUser
    void updateDpt_NotFound_InternalServerError() throws Exception {
        // given
        Long departmentId = 99L;
        MasterDataDto updateDto = new MasterDataDto(departmentId, "새이름");
        given(departmentService.updateDpt(anyLong(), any(MasterDataDto.class)))
                .willThrow(new EntityNotFoundException("부서를 찾을 수 없음")); // 서비스에서 발생

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isInternalServerError()) // Controller의 일반 Exception 핸들러에 의해 처리
                .andExpect(jsonPath("$").value("부서 수정 중 오류 발생"))
                .andDo(print());
    }

    /**
     * <p>부서 이름 업데이트 중 `DataIntegrityViolationException` (DB 고유 제약 조건 위반) 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.updateDpt()` 호출 시 `DataIntegrityViolationException`을 발생시키도록 Mocking.</li>
     *     <li>`PATCH /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 400 Bad Request 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 이름 중복으로 DataIntegrityViolationException (관리자)")
    @WithMockUser
    void updateDpt_DataIntegrityViolation_BadRequest() throws Exception {
        // given
        Long departmentId = 1L;
        String newName = "기존부서"; // 이미 존재하는 이름
        MasterDataDto updateDto = new MasterDataDto(departmentId, newName);
        given(departmentService.updateDpt(anyLong(), any(MasterDataDto.class)))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("이미 등록된 부서가 있습니다."))
                .andDo(print());
    }

    /**
     * <p>부서 이름 업데이트 중 `IllegalArgumentException` 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.updateDpt()` 호출 시 `IllegalArgumentException`을 발생시키도록 Mocking.</li>
     *     <li>`PATCH /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 400 Bad Request 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 이름이 유효하지 않아 IllegalArgumentException (관리자)")
    @WithMockUser
    void updateDpt_IllegalArgument_BadRequest() throws Exception {
        // given
        Long departmentId = 1L;
        String newName = ""; // 유효하지 않은 이름
        MasterDataDto updateDto = new MasterDataDto(departmentId, newName);
        given(departmentService.updateDpt(anyLong(), any(MasterDataDto.class)))
                .willThrow(new IllegalArgumentException("부서 이름은 필수입니다."));

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("부서 이름은 필수입니다."))
                .andDo(print());
    }

    /**
     * <p>부서 이름 업데이트 중 서비스 내부 오류 발생 테스트 (관리자 권한).</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentService.updateDpt()` 호출 시 `RuntimeException`을 발생시키도록 Mocking.</li>
     *     <li>`PATCH /admin/department/{id}` 요청 수행.</li>
     *     <li>HTTP 500 Internal Server Error 상태 코드와 오류 메시지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 부서 이름 업데이트 중 내부 서버 오류 (관리자)")
    @WithMockUser
    void updateDpt_InternalServerError() throws Exception {
        // given
        Long departmentId = 1L;
        String newName = "새이름";
        MasterDataDto updateDto = new MasterDataDto(departmentId, newName);
        given(departmentService.updateDpt(anyLong(), any(MasterDataDto.class)))
                .willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf())
        );

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("부서 수정 중 오류 발생"))
                .andDo(print());
    }
}
