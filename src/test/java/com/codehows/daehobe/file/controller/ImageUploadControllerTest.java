package com.codehows.daehobe.file.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageUploadController.class)
@Import(JwtService.class)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    @DisplayName("성공: 이미지 업로드")
    @WithMockUser
    void uploadImage_Success() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test image".getBytes());
        String savedFileName = "saved-test.jpg";
        given(fileService.storeEmbedImage(any())).willReturn(savedFileName);

        // when
        ResultActions result = mockMvc.perform(multipart("/upload-image")
                .file(image)
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/file/" + savedFileName))
                .andExpect(jsonPath("$.fileName").value(savedFileName));
    }

    @Test
    @DisplayName("실패: 빈 파일 업로드")
    @WithMockUser
    void uploadImage_EmptyFile() throws Exception {
        // given
        MockMultipartFile emptyFile = new MockMultipartFile("image", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        // when
        ResultActions result = mockMvc.perform(multipart("/upload-image")
                .file(emptyFile)
                .with(csrf()));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("파일이 비어있습니다."));
    }

    @Test
    @DisplayName("실패: 이미지 파일이 아님")
    @WithMockUser
    void uploadImage_NotAnImage() throws Exception {
        // given
        MockMultipartFile notAnImage = new MockMultipartFile("image", "test.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes());

        // when
        ResultActions result = mockMvc.perform(multipart("/upload-image")
                .file(notAnImage)
                .with(csrf()));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("이미지 파일만 업로드 가능합니다."));
    }

    @Test
    @DisplayName("실패: 서비스 예외 발생")
    @WithMockUser
    void uploadImage_ServiceException() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test image".getBytes());
        given(fileService.storeEmbedImage(any())).willThrow(new RuntimeException("저장 실패"));

        // when
        ResultActions result = mockMvc.perform(multipart("/upload-image")
                .file(image)
                .with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("이미지 업로드 실패: 저장 실패"));
    }
}
