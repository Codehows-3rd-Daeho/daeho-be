package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(CategoryController.class)
@Import(JwtService.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    private final String ADMIN_API_PATH_PREFIX = "/admin/category";
    private final String PUBLIC_API_PATH_PREFIX = "/masterData/category";

    @Test
    @DisplayName("성공: 모든 카테고리 조회")
    @WithMockUser
    void getCategory_Success() throws Exception {
        // given
        List<MasterDataDto> categories = Arrays.asList(
                new MasterDataDto(1L, "카테고리1"),
                new MasterDataDto(2L, "카테고리2")
        );
        given(categoryService.findAll()).willReturn(categories);

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("카테고리1"))
                .andExpect(jsonPath("$[1].name").value("카테고리2"));
    }

    @Test
    @DisplayName("성공: 카테고리 없음 시 모든 카테고리 조회")
    @WithMockUser
    void getCategory_EmptyList() throws Exception {
        // given
        given(categoryService.findAll()).willReturn(Collections.emptyList());

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("실패: 카테고리 조회 중 내부 서버 오류")
    @WithMockUser
    void getCategory_InternalServerError() throws Exception {
        // given
        given(categoryService.findAll()).willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions resultActions = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("카테고리 조회 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 카테고리 생성 (관리자)")
    @WithMockUser
    void createCategory_Success() throws Exception {
        // given
        MasterDataDto createDto = new MasterDataDto(null, "새카테고리");
        Category createdCategory = Category.builder().id(3L).name("새카테고리").build();
        given(categoryService.createCategory(any(MasterDataDto.class))).willReturn(createdCategory);

        // when
        ResultActions resultActions = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새카테고리"));
    }
    
    @Test
    @DisplayName("실패: 중복 이름으로 카테고리 생성 시 Bad Request (관리자)")
    @WithMockUser
    void createCategory_DuplicateName_BadRequest() throws Exception {
        // given
        MasterDataDto createDto = new MasterDataDto(null, "중복카테고리");
        given(categoryService.createCategory(any(MasterDataDto.class)))
                .willThrow(new IllegalArgumentException("이미 존재하는 카테고리입니다: 중복카테고리"));

        // when
        ResultActions resultActions = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto))
                .with(csrf()));
        
        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("이미 존재하는 카테고리입니다: 중복카테고리"));
    }

    @Test
    @DisplayName("성공: 카테고리 삭제 (관리자)")
    @WithMockUser
    void deleteCategory_Success() throws Exception {
        // given
        Long categoryId = 1L;
        doNothing().when(categoryService).deleteCategory(categoryId);

        // when
        ResultActions resultActions = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", categoryId)
                .with(csrf()));

        // then
        resultActions.andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("실패: 카테고리 삭제 중 내부 서버 오류 (관리자)")
    @WithMockUser
    void deleteCategory_InternalServerError() throws Exception {
        // given
        Long categoryId = 99L;
        doThrow(new EntityNotFoundException("카테고리를 찾을 수 없음")).when(categoryService).deleteCategory(categoryId);

        // when
        ResultActions resultActions = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", categoryId)
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("카테고리 삭제 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 카테고리 이름 업데이트 (관리자)")
    @WithMockUser
    void updateCategory_Success() throws Exception {
        // given
        Long categoryId = 1L;
        String updatedName = "업데이트된카테고리";
        MasterDataDto updateDto = new MasterDataDto(categoryId, updatedName);
        Category updatedCategory = Category.builder().id(categoryId).name(updatedName).build();

        given(categoryService.updateCategory(anyLong(), any(MasterDataDto.class))).willReturn(updatedCategory);

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(updatedName));
    }
    
    @Test
    @DisplayName("실패: 카테고리 이름 중복으로 DataIntegrityViolationException (관리자)")
    @WithMockUser
    void updateCategory_DataIntegrityViolation_BadRequest() throws Exception {
        // given
        Long categoryId = 1L;
        MasterDataDto updateDto = new MasterDataDto(categoryId, "기존카테고리");
        given(categoryService.updateCategory(anyLong(), any(MasterDataDto.class)))
                .willThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("이미 등록된 카테고리가 있습니다."));
    }

    @Test
    @DisplayName("실패: 카테고리 이름 업데이트 중 내부 서버 오류 (관리자)")
    @WithMockUser
    void updateCategory_InternalServerError() throws Exception {
        // given
        Long categoryId = 1L;
        MasterDataDto updateDto = new MasterDataDto(categoryId, "새이름");
        given(categoryService.updateCategory(anyLong(), any(MasterDataDto.class)))
                .willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions resultActions = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .with(csrf()));

        // then
        resultActions
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("카테고리 수정 중 오류 발생"));
    }
}
