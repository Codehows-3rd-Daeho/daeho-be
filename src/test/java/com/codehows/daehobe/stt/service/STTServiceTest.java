package com.codehows.daehobe.stt.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.processing.SttEncodingTaskExecutor;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class STTServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private STTRepository sttRepository;
    @Mock private FileService fileService;
    @Mock private SttProvider sttProvider;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate hashRedisTemplate;
    @Mock private SttCacheService sttCacheService;
    @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock private SttEncodingTaskExecutor sttEncodingTaskExecutor;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    private STTService sttService;

    private STT testStt;
    private File testAudioFile;
    private Meeting testMeeting;

    @BeforeEach
    void setUp() {
        lenient().when(hashRedisTemplate.opsForValue()).thenReturn(valueOperations);
        sttService = new STTService(
            meetingRepository, sttRepository, fileService,
            sttProvider, hashRedisTemplate, sttCacheService,
            messagingTemplate, sttEncodingTaskExecutor
        );
        ReflectionTestUtils.setField(sttService, "fileLocation", "/tmp/stt_test");
        ReflectionTestUtils.setField(sttService, "heartbeatTtl", 30L);

        testMeeting = Meeting.builder().id(1L).title("테스트 회의").build();
        testAudioFile = File.builder()
                .fileId(10L)
                .path("/file/test.wav")
                .originalName("test.wav")
                .savedName("test.wav")
                .size(1024L)
                .targetId(1L)
                .targetType(TargetType.STT)
                .build();
        testStt = STT.builder()
                .id(1L)
                .meeting(testMeeting)
                .summary("요약 내용")
                .content("원본 내용")
                .status(STT.Status.COMPLETED)
                .build();

    }

    @Test
    @DisplayName("성공: ID로 STT 조회")
    void getSTTById_Success() {
        // given
        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(testStt));
        when(fileService.getSTTFile(anyLong())).thenReturn(testAudioFile);

        // when
        STTDto result = sttService.getSTTById(1L);

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        assertThat(result.getFile().getOriginalName()).isEqualTo(testAudioFile.getOriginalName());
    }

    @Test
    @DisplayName("성공: 캐시에서 동적 STT 상태 조회 (캐시 히트)")
    void getDynamicSttStatus_CacheHit_Success() {
        // given
        STTDto cachedDto = STTDto.fromEntity(testStt, FileDto.fromEntity(testAudioFile));
        when(sttCacheService.getCachedSttStatus(testStt.getId())).thenReturn(cachedDto);

        // when
        STTDto result = sttService.getDynamicSttStatus(testStt.getId());

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(sttCacheService).getCachedSttStatus(testStt.getId());
        verify(sttRepository, never()).findById(anyLong()); // 캐시 히트 시 DB 조회 없음
    }

    @Test
    @DisplayName("성공: 캐시에서 동적 STT 상태 조회 (캐시 미스 -> DB 조회)")
    void getDynamicSttStatus_CacheMiss_Success() {
        // given
        when(sttCacheService.getCachedSttStatus(testStt.getId())).thenReturn(null);
        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(testStt));
        when(fileService.getSTTFile(anyLong())).thenReturn(testAudioFile);

        // when
        STTDto result = sttService.getDynamicSttStatus(testStt.getId());

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(sttCacheService).getCachedSttStatus(testStt.getId());
        verify(sttRepository).findById(anyLong()); // 캐시 미스 시 DB 조회
    }

    @Test
    @DisplayName("성공: 회의 ID로 STT 목록 조회")
    void getSTTsByMeetingId_Success() {
        // given
        when(meetingRepository.findById(anyLong())).thenReturn(Optional.of(testMeeting));
        when(sttRepository.findByMeetingIdWithStatusCondition(anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(testStt));
        when(fileService.getSTTFiles(anyList())).thenReturn(Collections.singletonList(testAudioFile));

        // when
        List<STTDto> result = sttService.getSTTsByMeetingId(testMeeting.getId(), 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(testStt.getId());
        assertThat(result.get(0).getFile().getOriginalName()).isEqualTo(testAudioFile.getOriginalName());
    }

    @Test
    @DisplayName("성공: STT 요약 업데이트")
    void updateSummary_Success() {
        // given
        String newSummary = "새로운 요약";
        STT spyStt = spy(testStt); // 엔티티 메서드 호출 검증을 위해 스파이 사용
        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(spyStt));

        // when
        sttService.updateSummary(testStt.getId(), newSummary);

        // then
        verify(spyStt).updateSummary(newSummary); // 엔티티 메서드 호출 검증
    }

    @Test
    @DisplayName("성공: STT 녹음 시작")
    void startRecording_Success() {
        // given
        when(meetingRepository.findById(anyLong())).thenReturn(Optional.of(testMeeting));
        when(sttRepository.save(any(STT.class))).thenReturn(testStt);
        when(fileService.createFile(anyString(), anyLong(), any(TargetType.class))).thenReturn(testAudioFile);

        // when
        STTDto result = sttService.startRecording(testMeeting.getId());

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(sttCacheService).cacheSttStatus(any(STTDto.class));
        verify(messagingTemplate).convertAndSend(anyString(), any(STTDto.class));
    }

    @Test
    @DisplayName("성공: STT 파일 업로드 및 번역 요청")
    void uploadAndTranslate_Success() {
        // given
        MockMultipartFile mockFile = new MockMultipartFile("audio", "audio.wav", "audio/wav", "audio data".getBytes());

        when(meetingRepository.findById(anyLong())).thenReturn(Optional.of(testMeeting));
        when(sttProvider.requestTranscription(any(Resource.class))).thenReturn("rid123");
        when(sttRepository.save(any(STT.class))).thenReturn(testStt);
        when(fileService.uploadFiles(anyLong(), anyList(), any(TargetType.class))).thenReturn(Collections.singletonList(testAudioFile));

        // when
        STTDto result = sttService.uploadAndTranslate(testMeeting.getId(), mockFile);

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(sttCacheService).cacheSttStatus(any(STTDto.class));
        // DB status is PROCESSING, scheduler will pick it up automatically (no Kafka)
    }

    @Test
    @DisplayName("성공: STT 삭제 - Redis 키 + 파일 + DB 삭제")
    void deleteSTT_Success() {
        // given
        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(testStt));
        when(fileService.getSTTFile(anyLong())).thenReturn(testAudioFile);
        when(hashRedisTemplate.delete(anyString())).thenReturn(true);

        // when
        sttService.deleteSTT(testStt.getId());

        // then
        verify(sttRepository).delete(testStt);
        verify(fileService).updateFiles(eq(testStt.getId()), eq(null), eq(List.of(testAudioFile.getFileId())), eq(TargetType.STT));
        verify(hashRedisTemplate, times(2)).delete(anyString()); // status key + heartbeat key
    }

    @Test
    @DisplayName("실패: STT 삭제 - 존재하지 않는 STT")
    void deleteSTT_NotFound() {
        // given
        Long invalidId = 999L;
        when(sttRepository.findById(invalidId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sttService.deleteSTT(invalidId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("성공: 청크 추가 (finish=false) - Heartbeat 갱신")
    void appendChunk_NotFinished_HeartbeatRefreshed() {
        // given
        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk.wav", "audio/wav", "chunk data".getBytes());
        STT recordingStt = STT.builder()
                .id(1L)
                .meeting(testMeeting)
                .status(STT.Status.RECORDING)
                .build();

        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(recordingStt));
        when(fileService.appendChunk(anyLong(), any(), any(TargetType.class))).thenReturn(testAudioFile);

        // when
        STTDto result = sttService.appendChunk(recordingStt.getId(), chunk, false);

        // then
        assertThat(result.getId()).isEqualTo(recordingStt.getId());
        verify(valueOperations).set(
                eq("stt:recording:heartbeat:" + recordingStt.getId()),
                eq(""),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );
        verify(sttEncodingTaskExecutor, never()).submitEncodingTask(anyLong());
    }

    @Test
    @DisplayName("성공: 청크 추가 (finish=true) - ENCODING 전이 및 비동기 태스크 제출")
    void appendChunk_Finished_TransitionToEncoding() {
        // given
        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk.wav", "audio/wav", "chunk data".getBytes());
        STT recordingStt = spy(STT.builder()
                .id(1L)
                .meeting(testMeeting)
                .status(STT.Status.RECORDING)
                .build());

        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(recordingStt));
        when(fileService.appendChunk(anyLong(), any(), any(TargetType.class))).thenReturn(testAudioFile);
        when(hashRedisTemplate.delete(anyString())).thenReturn(true);

        // when
        STTDto result = sttService.appendChunk(recordingStt.getId(), chunk, true);

        // then
        verify(recordingStt).setStatus(STT.Status.ENCODING);
        verify(sttCacheService).cacheSttStatus(any(STTDto.class));
        verify(messagingTemplate).convertAndSend(anyString(), any(STTDto.class));
        verify(hashRedisTemplate).delete("stt:recording:heartbeat:" + recordingStt.getId());
        verify(sttEncodingTaskExecutor).submitEncodingTask(recordingStt.getId());
    }

    @Test
    @DisplayName("실패: 청크 추가 - 존재하지 않는 STT")
    void appendChunk_NotFound() {
        // given
        Long invalidId = 999L;
        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk.wav", "audio/wav", "chunk data".getBytes());
        when(sttRepository.findById(invalidId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sttService.appendChunk(invalidId, chunk, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid STT ID");
    }

    @Test
    @DisplayName("성공: 녹음된 파일 번역 시작 - 폴링 셋 등록 및 RID 업데이트")
    void startTranslateForRecorded_Success() {
        // given
        STT encodedStt = STT.builder()
                .id(1L)
                .meeting(testMeeting)
                .status(STT.Status.ENCODED)
                .build();
        String expectedRid = "rid-12345";

        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(encodedStt));
        when(fileService.getSTTFile(anyLong())).thenReturn(testAudioFile);
        when(sttProvider.requestTranscription(any(Resource.class))).thenReturn(expectedRid);

        // when
        STTDto result = sttService.startTranslateForRecorded(encodedStt.getId());

        // then
        assertThat(result.getStatus()).isEqualTo(STT.Status.PROCESSING);
        assertThat(result.getRid()).isEqualTo(expectedRid);
        verify(sttCacheService).cacheSttStatus(any(STTDto.class));
        verify(sttCacheService).addToPollingSet(encodedStt.getId(), STT.Status.PROCESSING);
        verify(messagingTemplate).convertAndSend(anyString(), any(STTDto.class));
    }
}
