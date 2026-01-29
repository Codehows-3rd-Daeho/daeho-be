package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.AllowedExtension;
import com.codehows.daehobe.masterData.service.FileSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityExistsException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(FileSettingController.class)
@Import(JwtService.class)
class FileSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FileSettingService fileSettingService;

    @Nested
    @DisplayName("파일 용량 설정 테스트")
    class FileSizeTests {

        private final String GET_SIZE_PATH = "/file/size";
        private final String SAVE_SIZE_PATH = "/admin/file/size";

        @Test
        @DisplayName("성공: 파일 용량 조회")
        @WithMockUser
        void getFileSize_Success() throws Exception {
            // given
            MasterDataDto fileSizeDto = new MasterDataDto(1L, String.valueOf(5 * 1024 * 1024)); // 5MB
            given(fileSettingService.getFileSize()).willReturn(fileSizeDto);

            // when
            ResultActions result = mockMvc.perform(get(GET_SIZE_PATH).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(String.valueOf(5 * 1024 * 1024)));
        }

        @Test
        @DisplayName("성공: 파일 용량 저장")
        @WithMockUser
        void saveFileSize_Success() throws Exception {
            // given
            MasterDataDto fileSizeDto = new MasterDataDto(1L, "10"); // 10MB
            doNothing().when(fileSettingService).saveFileSize(any(MasterDataDto.class));

            // when
            ResultActions result = mockMvc.perform(post(SAVE_SIZE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(fileSizeDto))
                    .with(csrf()));

            // then
            result.andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("파일 확장자 설정 테스트")
    class FileExtensionTests {

        private final String GET_EXT_PATH = "/file/extension";
        private final String ADMIN_EXT_PATH = "/admin/file/extension";

        @Test
        @DisplayName("성공: 모든 확장자 조회")
        @WithMockUser
        void getExtensions_Success() throws Exception {
            // given
            given(fileSettingService.getExtensions()).willReturn(Arrays.asList(
                    new MasterDataDto(1L, "txt"),
                    new MasterDataDto(2L, "pdf")
            ));

            // when
            ResultActions result = mockMvc.perform(get(GET_EXT_PATH).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("txt"))
                    .andExpect(jsonPath("$[1].name").value("pdf"));
        }

        @Test
        @DisplayName("성공: 확장자 없음 시 모든 확장자 조회")
        @WithMockUser
        void getExtensions_Empty() throws Exception {
            // given
            given(fileSettingService.getExtensions()).willReturn(Collections.emptyList());

            // when
            ResultActions result = mockMvc.perform(get(GET_EXT_PATH).with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("성공: 확장자 저장")
        @WithMockUser
        void saveExtension_Success() throws Exception {
            // given
            MasterDataDto extDto = new MasterDataDto(null, "jpg");
            AllowedExtension savedExt = AllowedExtension.builder().id(1L).name("jpg").build();
            given(fileSettingService.saveExtension(any(MasterDataDto.class))).willReturn(savedExt);

            // when
            ResultActions result = mockMvc.perform(post(ADMIN_EXT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(extDto))
                    .with(csrf()));

            // then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("jpg"));
        }

        @Test
        @DisplayName("실패: 중복 확장자 저장")
        @WithMockUser
        void saveExtension_Duplicate() throws Exception {
            // given
            MasterDataDto extDto = new MasterDataDto(null, "jpg");
            given(fileSettingService.saveExtension(any(MasterDataDto.class)))
                    .willThrow(new IllegalArgumentException("이미 존재하는 확장자입니다: jpg"));

            // when
            ResultActions result = mockMvc.perform(post(ADMIN_EXT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(extDto))
                    .with(csrf()));

            // then
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("이미 존재하는 확장자입니다: jpg"));
        }

        @Test
        @DisplayName("성공: 확장자 삭제")
        @WithMockUser
        void deleteExtension_Success() throws Exception {
            // given
            Long extId = 1L;
            doNothing().when(fileSettingService).deleteExtension(extId);

            // when
            ResultActions result = mockMvc.perform(delete(ADMIN_EXT_PATH + "/{id}", extId).with(csrf()));

            // then
            result.andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 삭제할 확장자 없음")
        @WithMockUser
        void deleteExtension_NotFound() throws Exception {
            // given
            Long extId = 99L;
            doThrow(new EntityExistsException()).when(fileSettingService).deleteExtension(extId);

            // when
            ResultActions result = mockMvc.perform(delete(ADMIN_EXT_PATH + "/{id}", extId).with(csrf()));

            // then
            result.andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$").value("확장자 삭제 중 오류 발생"));
        }
    }
}
