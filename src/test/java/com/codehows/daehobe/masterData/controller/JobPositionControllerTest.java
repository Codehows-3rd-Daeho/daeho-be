package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.service.JobPositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(JobPositionController.class)
@Import(JwtService.class)
class JobPositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobPositionService jobPositionService;

    private final String ADMIN_API_PATH_PREFIX = "/admin/jobPosition";
    private final String PUBLIC_API_PATH_PREFIX = "/masterData/jobPosition";

    @Test
    @DisplayName("성공: 모든 직급 조회")
    @WithMockUser
    void getJobPosition_Success() throws Exception {
        // given
        given(jobPositionService.findAll()).willReturn(Arrays.asList(
                new MasterDataDto(1L, "사원"),
                new MasterDataDto(2L, "대리")
        ));

        // when
        ResultActions result = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("사원"))
                .andExpect(jsonPath("$[1].name").value("대리"));
    }

    @Test
    @DisplayName("성공: 직급 생성")
    @WithMockUser
    void createPosition_Success() throws Exception {
        // given
        MasterDataDto dto = new MasterDataDto(null, "과장");
        JobPosition pos = JobPosition.builder().id(3L).name("과장").build();
        given(jobPositionService.createPos(any(MasterDataDto.class))).willReturn(pos);

        // when
        ResultActions result = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("과장"));
    }
    
    @Test
    @DisplayName("실패: 중복 직급 생성")
    @WithMockUser
    void createPosition_Duplicate() throws Exception {
        // given
        MasterDataDto dto = new MasterDataDto(null, "대리");
        given(jobPositionService.createPos(any(MasterDataDto.class)))
                .willThrow(new IllegalArgumentException("이미 존재하는 직급입니다: 대리"));
        
        // when
        ResultActions result = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("이미 존재하는 직급입니다: 대리"));
    }

    @Test
    @DisplayName("성공: 직급 삭제")
    @WithMockUser
    void deletePosition_Success() throws Exception {
        // given
        Long posId = 1L;
        doNothing().when(jobPositionService).deletePos(posId);

        // when
        ResultActions result = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", posId).with(csrf()));

        // then
        result.andExpect(status().isNoContent());
    }
    
    @Test
    @DisplayName("실패: 직급 삭제 중 오류")
    @WithMockUser
    void deletePosition_Error() throws Exception {
        // given
        Long posId = 1L;
        doThrow(new RuntimeException("DB 오류")).when(jobPositionService).deletePos(posId);
        
        // when
        ResultActions result = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", posId).with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
              .andExpect(jsonPath("$").value("직급 삭제 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 직급 업데이트")
    @WithMockUser
    void updatePosition_Success() throws Exception {
        // given
        Long posId = 1L;
        MasterDataDto dto = new MasterDataDto(posId, "차장");
        JobPosition pos = JobPosition.builder().id(posId).name("차장").build();
        given(jobPositionService.updatePos(anyLong(), any(MasterDataDto.class))).willReturn(pos);

        // when
        ResultActions result = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", posId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("차장"));
    }
    
    @Test
    @DisplayName("실패: 직급 업데이트 중 중복 이름")
    @WithMockUser
    void updatePosition_Duplicate() throws Exception {
        // given
        Long posId = 1L;
        MasterDataDto dto = new MasterDataDto(posId, "대리");
        given(jobPositionService.updatePos(anyLong(), any(MasterDataDto.class)))
                .willThrow(new DataIntegrityViolationException("이미 등록된 직급이 있습니다."));
                
        // when
        ResultActions result = mockMvc.perform(patch(ADMIN_API_PATH_PREFIX + "/{id}", posId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(csrf()));
                
        // then
        result.andExpect(status().isBadRequest())
              .andExpect(jsonPath("$").value("이미 등록된 직급이 있습니다."));
    }
}
