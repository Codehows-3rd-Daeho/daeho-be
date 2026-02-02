package com.codehows.daehobe.stt.service.processing;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.stt.exception.SttNotCompletedException;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class SttJobProcessorTest {

    @Mock
    private STTRepository sttRepository;
    @Mock
    private FileService fileService;
    @Mock
    private SttProvider sttProvider;
    @Mock
    private SttCacheService sttCacheService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SttJobProcessor sttJobProcessor;

    private Meeting testMeeting;
    private STT testStt;
    private File testFile;

    @BeforeEach
    void setUp() {
        sttJobProcessor = new SttJobProcessor(
                sttRepository, fileService, sttProvider,
                sttCacheService, messagingTemplate
        );
        ReflectionTestUtils.setField(sttJobProcessor, "appBaseUrl", "http://localhost:8080");

        testMeeting = Meeting.builder().id(1L).title("테스트 회의").build();
        testFile = File.builder()
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
                .rid("test-rid")
                .status(STT.Status.PROCESSING)
                .build();
    }

    @Nested
    @DisplayName("handleAbnormalTermination 테스트")
    class HandleAbnormalTerminationTest {

        @Test
        @DisplayName("성공: RECORDING 상태에서 비정상 종료 복구")
        void handleAbnormalTermination_RecordingState_Recovery() throws Exception {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.RECORDING)
                    .file(FileDto.fromEntity(testFile))
                    .chunkingCnt(0)
                    .build();

            // spy를 사용하여 isFileReadyToBeServed 메서드 스텁
            SttJobProcessor spyProcessor = spy(sttJobProcessor);
            doReturn(true).when(spyProcessor).isFileReadyToBeServed(any(File.class));

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(fileService.getSTTFile(sttId)).thenReturn(testFile);
            when(fileService.encodeAudioFile(any(File.class))).thenReturn(testFile);
            when(sttRepository.findById(sttId)).thenReturn(Optional.of(testStt));
            when(sttRepository.save(any(STT.class))).thenReturn(testStt);

            // when
            spyProcessor.handleAbnormalTermination(sttId);

            // then
            verify(sttCacheService, atLeast(2)).cacheSttStatus(any(STTDto.class));
            verify(messagingTemplate, atLeast(2)).convertAndSend(anyString(), any(STTDto.class));
        }

        @Test
        @DisplayName("무시: RECORDING이 아닌 상태에서 조기 반환")
        void handleAbnormalTermination_NotRecording_EarlyReturn() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.PROCESSING)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttJobProcessor.handleAbnormalTermination(sttId);

            // then
            verify(fileService, never()).getSTTFile(anyLong());
            verify(fileService, never()).encodeAudioFile(any(File.class));
        }

        @Test
        @DisplayName("무시: 캐시 없음 시 조기 반환")
        void handleAbnormalTermination_CacheNotFound_EarlyReturn() {
            // given
            Long sttId = 1L;
            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(null);

            // when
            sttJobProcessor.handleAbnormalTermination(sttId);

            // then
            verify(fileService, never()).getSTTFile(anyLong());
        }
    }

    @Nested
    @DisplayName("processSingleEncodingJob 테스트")
    class ProcessSingleEncodingJobTest {

        @Test
        @DisplayName("성공: 인코딩 완료 후 ENCODED 상태로 전이")
        void processSingleEncodingJob_Success() throws Exception {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.ENCODING)
                    .file(FileDto.fromEntity(testFile))
                    .chunkingCnt(0)
                    .build();

            // spy를 사용하여 isFileReadyToBeServed 메서드 스텁
            SttJobProcessor spyProcessor = spy(sttJobProcessor);
            doReturn(true).when(spyProcessor).isFileReadyToBeServed(any(File.class));

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(fileService.getSTTFile(sttId)).thenReturn(testFile);
            when(fileService.encodeAudioFile(any(File.class))).thenReturn(testFile);
            when(sttRepository.findById(sttId)).thenReturn(Optional.of(testStt));
            when(sttRepository.save(any(STT.class))).thenReturn(testStt);

            // when
            spyProcessor.processSingleEncodingJob(sttId);

            // then
            verify(fileService).encodeAudioFile(any(File.class));
            verify(sttCacheService, atLeast(2)).cacheSttStatus(any(STTDto.class));
            verify(sttRepository).save(any(STT.class));
        }

        @Test
        @DisplayName("실패: 캐시 없음")
        void processSingleEncodingJob_CacheNotFound() {
            // given
            Long sttId = 1L;
            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> sttJobProcessor.processSingleEncodingJob(sttId))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패: 인코딩 실패")
        void processSingleEncodingJob_EncodingFailed() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.ENCODING)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(fileService.getSTTFile(sttId)).thenReturn(testFile);
            when(fileService.encodeAudioFile(any(File.class))).thenThrow(new RuntimeException("Encoding failed"));

            // when & then
            assertThatThrownBy(() -> sttJobProcessor.processSingleEncodingJob(sttId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("processSingleSttJob 테스트")
    class ProcessSingleSttJobTest {

        @Test
        @DisplayName("성공: 변환 완료 시 SUMMARIZING 전이")
        void processSingleSttJob_Completed_TransitionToSummarizing() {
            // given
            Long sttId = 1L;
            String rid = "test-rid";
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.PROCESSING)
                    .rid(rid)
                    .build();

            SttTranscriptionResult completedResult = SttTranscriptionResult.builder()
                    .completed(true)
                    .content("변환된 텍스트 내용")
                    .progress(100)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(sttProvider.checkTranscriptionStatus(rid)).thenReturn(completedResult);
            when(sttProvider.requestSummary(anyString())).thenReturn("summary-rid");

            // when
            sttJobProcessor.processSingleSttJob(sttId);

            // then
            verify(sttProvider).requestSummary(anyString());
            verify(sttCacheService).removeFromPollingSet(sttId, STT.Status.PROCESSING);
            verify(sttCacheService).addToPollingSet(sttId, STT.Status.SUMMARIZING);
            verify(sttCacheService).resetRetryCount(sttId);
        }

        @Test
        @DisplayName("진행중: SttNotCompletedException 발생")
        void processSingleSttJob_InProgress_ThrowsException() {
            // given
            Long sttId = 1L;
            String rid = "test-rid";
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.PROCESSING)
                    .rid(rid)
                    .build();

            SttTranscriptionResult inProgressResult = SttTranscriptionResult.builder()
                    .completed(false)
                    .content("")
                    .progress(50)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(sttProvider.checkTranscriptionStatus(rid)).thenReturn(inProgressResult);

            // when & then
            assertThatThrownBy(() -> sttJobProcessor.processSingleSttJob(sttId))
                    .isInstanceOf(SttNotCompletedException.class);
        }

        @Test
        @DisplayName("무시: 캐시 미스 시 스킵")
        void processSingleSttJob_CacheMiss_Skip() {
            // given
            Long sttId = 1L;
            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(null);

            // when
            sttJobProcessor.processSingleSttJob(sttId);

            // then
            verify(sttProvider, never()).checkTranscriptionStatus(anyString());
        }

        @Test
        @DisplayName("무시: 상태 불일치 시 스킵")
        void processSingleSttJob_StatusMismatch_Skip() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.ENCODING) // PROCESSING이 아님
                    .rid("test-rid")
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttJobProcessor.processSingleSttJob(sttId);

            // then
            verify(sttProvider, never()).checkTranscriptionStatus(anyString());
        }

        @Test
        @DisplayName("무시: RID 없음 시 스킵")
        void processSingleSttJob_NoRid_Skip() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.PROCESSING)
                    .rid(null) // RID 없음
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttJobProcessor.processSingleSttJob(sttId);

            // then
            verify(sttProvider, never()).checkTranscriptionStatus(anyString());
        }
    }

    @Nested
    @DisplayName("processSingleSummaryJob 테스트")
    class ProcessSingleSummaryJobTest {

        @Test
        @DisplayName("성공: 요약 완료 시 COMPLETED + DB 저장")
        void processSingleSummaryJob_Completed_DbSave() {
            // given
            Long sttId = 1L;
            String summaryRid = "summary-rid";
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.SUMMARIZING)
                    .rid("test-rid")
                    .summaryRid(summaryRid)
                    .content("변환된 텍스트")
                    .chunkingCnt(0)
                    .build();

            SttSummaryResult completedResult = SttSummaryResult.builder()
                    .completed(true)
                    .summaryText("요약 내용")
                    .progress(100)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(sttProvider.checkSummaryStatus(summaryRid)).thenReturn(completedResult);
            when(sttRepository.findById(sttId)).thenReturn(Optional.of(testStt));
            when(sttRepository.save(any(STT.class))).thenReturn(testStt);

            // when
            sttJobProcessor.processSingleSummaryJob(sttId);

            // then
            verify(sttCacheService).removeFromPollingSet(sttId, STT.Status.SUMMARIZING);
            verify(sttCacheService).resetRetryCount(sttId);
            verify(sttRepository).findById(sttId);
            verify(sttRepository).save(any(STT.class));
        }

        @Test
        @DisplayName("진행중: SttNotCompletedException 발생")
        void processSingleSummaryJob_InProgress_ThrowsException() {
            // given
            Long sttId = 1L;
            String summaryRid = "summary-rid";
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .meetingId(1L)
                    .status(STT.Status.SUMMARIZING)
                    .summaryRid(summaryRid)
                    .build();

            SttSummaryResult inProgressResult = SttSummaryResult.builder()
                    .completed(false)
                    .summaryText("")
                    .progress(50)
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);
            when(sttProvider.checkSummaryStatus(summaryRid)).thenReturn(inProgressResult);

            // when & then
            assertThatThrownBy(() -> sttJobProcessor.processSingleSummaryJob(sttId))
                    .isInstanceOf(SttNotCompletedException.class);
        }

        @Test
        @DisplayName("무시: 상태 불일치 시 스킵")
        void processSingleSummaryJob_StatusMismatch_Skip() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.PROCESSING) // SUMMARIZING이 아님
                    .summaryRid("summary-rid")
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttJobProcessor.processSingleSummaryJob(sttId);

            // then
            verify(sttProvider, never()).checkSummaryStatus(anyString());
        }

        @Test
        @DisplayName("무시: summaryRid 없음 시 스킵")
        void processSingleSummaryJob_NoSummaryRid_Skip() {
            // given
            Long sttId = 1L;
            STTDto cachedDto = STTDto.builder()
                    .id(sttId)
                    .status(STT.Status.SUMMARIZING)
                    .summaryRid(null) // summaryRid 없음
                    .build();

            when(sttCacheService.getCachedSttStatus(sttId)).thenReturn(cachedDto);

            // when
            sttJobProcessor.processSingleSummaryJob(sttId);

            // then
            verify(sttProvider, never()).checkSummaryStatus(anyString());
        }
    }
}
