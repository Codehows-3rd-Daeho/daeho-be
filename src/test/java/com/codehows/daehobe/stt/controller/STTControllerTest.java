package com.codehows.daehobe.stt.controller;

import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.StartRecordingRequest;
import com.codehows.daehobe.stt.service.STTService;
import com.codehows.daehobe.stt.entity.STT; // STT 엔티티 임포트
import org.springframework.mock.web.MockPart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile; // MultipartFile 임포트
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(STTController.class)
@Import(JwtService.class)
class STTControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private STTService sttService;

    private final Long TEST_STT_ID = 1L;
    private final Long TEST_MEETING_ID = 10L;
    private final Long TEST_MEMBER_ID = 1L;

    @Test
    @DisplayName("성공: 회의 ID로 STT 목록 조회")
    @WithMockUser(username = "1")
    void getSTTs_Success() throws Exception {
        // given
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).summary("테스트 요약").build();
        given(sttService.getSTTsByMeetingId(eq(TEST_MEETING_ID), eq(TEST_MEMBER_ID))).willReturn(Collections.singletonList(sttDto));

        // when
        ResultActions result = mockMvc.perform(get("/stt/meeting/{id}", TEST_MEETING_ID).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TEST_STT_ID))
                .andExpect(jsonPath("$[0].summary").value("테스트 요약"));
    }

    @Test
    @DisplayName("성공: STT 상태 조회")
    @WithMockUser
    void getSTTStatus_Success() throws Exception {
        // given
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).status(STT.Status.PROCESSING).build();
        given(sttService.getDynamicSttStatus(eq(TEST_STT_ID))).willReturn(sttDto);

        // when
        ResultActions result = mockMvc.perform(get("/stt/status/{id}", TEST_STT_ID).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_STT_ID))
                .andExpect(jsonPath("$.status").value(STT.Status.PROCESSING.name()));
    }

    @Test
    @DisplayName("성공: 녹음 시작")
    @WithMockUser
    void startRecording_Success() throws Exception {
        // given
        StartRecordingRequest request = new StartRecordingRequest();
        ReflectionTestUtils.setField(request, "meetingId", TEST_MEETING_ID);
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).status(STT.Status.RECORDING).build();
        given(sttService.startRecording(eq(TEST_MEETING_ID))).willReturn(sttDto);

        // when
        ResultActions result = mockMvc.perform(post("/stt/recording/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_STT_ID))
                .andExpect(jsonPath("$.status").value(STT.Status.RECORDING.name()));
    }

    @Test
    @DisplayName("성공: 청크 업로드")
    @WithMockUser
    void uploadChunk_Success() throws Exception {
        // given
        MockMultipartFile chunkFile = new MockMultipartFile("file", "chunk.wav", "audio/wav", "audio data".getBytes());
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).status(STT.Status.RECORDING).build();
        given(sttService.appendChunk(eq(TEST_STT_ID), any(MultipartFile.class), eq(true))).willReturn(sttDto);

        // when
        ResultActions result = mockMvc.perform(multipart("/stt/{sttId}/chunk", TEST_STT_ID)
                .file(chunkFile)
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_STT_ID))
                .andExpect(jsonPath("$.status").value(STT.Status.RECORDING.name()));
    }

    @Test
    @DisplayName("성공: STT 요약 업데이트")
    @WithMockUser
    void updateSTTSummary_Success() throws Exception {
        // given
        String content = "새로운 요약 내용";
        doNothing().when(sttService).updateSummary(eq(TEST_STT_ID), eq(content));

        // when
        ResultActions result = mockMvc.perform(patch("/stt/{id}/summary", TEST_STT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .with(csrf()));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("성공: STT 삭제")
    @WithMockUser
    void deleteSTT_Success() throws Exception {
        // given
        doNothing().when(sttService).deleteSTT(eq(TEST_STT_ID));

        // when
        ResultActions result = mockMvc.perform(delete("/stt/{id}", TEST_STT_ID).with(csrf()));

        // then
        result.andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("성공: 파일 업로드 및 번역 시작")
    @WithMockUser
    void uploadAndTranslate_Success() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "audio.wav", "audio/wav", "audio data".getBytes());
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).status(STT.Status.PROCESSING).build();
        given(sttService.uploadAndTranslate(eq(TEST_MEETING_ID), any(MultipartFile.class))).willReturn(sttDto);

        // when
        ResultActions result = mockMvc.perform(multipart("/stt/upload/{id}", TEST_STT_ID)
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_STT_ID))
                .andExpect(jsonPath("$.status").value(STT.Status.PROCESSING.name()));
    }

    @Test
    @DisplayName("성공: 녹음 완료 및 번역 시작")
    @WithMockUser
    void finishRecording_Success() throws Exception {
        // given
        MockMultipartFile chunkFile = new MockMultipartFile("file", "chunk.wav", "audio/wav", "audio data".getBytes());
        STTDto sttDto = STTDto.builder().id(TEST_STT_ID).status(STT.Status.PROCESSING).build();
        given(sttService.startTranslateForRecorded(eq(TEST_STT_ID))).willReturn(sttDto);

        // when
        ResultActions result = mockMvc.perform(multipart("/stt/{sttId}/chunk", TEST_STT_ID)
                        .file(chunkFile)
                        .part(new MockPart("finish", "true".getBytes()))
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                        .with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_STT_ID))
                .andExpect(jsonPath("$.status").value(STT.Status.PROCESSING.name()));
    }
}
