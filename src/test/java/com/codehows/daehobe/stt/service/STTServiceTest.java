package com.codehows.daehobe.stt.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.stt.entity.STT;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.stt.repository.STTRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.stt.service.cache.SttCacheService;
import com.codehows.daehobe.stt.service.provider.SttProvider;
import com.codehows.daehobe.common.utils.DataSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_STATUS_HASH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class STTServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private STTRepository sttRepository;
    @Mock private FileService fileService;
    @Mock private SttProvider sttProvider;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ValueOperations<String, String> valueOperations; // RedisTemplate의 opsForValue() 반환값
    @Mock private SttCacheService sttCacheService;

    private STTService sttService;

    private STT testStt;
    private File testAudioFile;
    private Meeting testMeeting;

    @BeforeEach
    void setUp() {
        sttService = new STTService(
            meetingRepository, sttRepository, fileService,
            sttProvider, kafkaTemplate, redisTemplate, sttCacheService
        );
        ReflectionTestUtils.setField(sttService, "fileLocation", "/tmp/stt_test");

        testMeeting = Meeting.builder().id(1L).title("테스트 회의").build();
        testAudioFile = File.builder()
                .fileId(10L)
                .path("/file/test.wav")
                .originalName("test.wav")
                .savedName("test.wav")
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
        
        // RedisTemplate의 opsForValue()가 Mock된 valueOperations를 반환하도록 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
        String jsonDto = DataSerializer.serialize(cachedDto);
        when(valueOperations.get(STT_STATUS_HASH_PREFIX + testStt.getId())).thenReturn(jsonDto);

        // when
        STTDto result = sttService.getDynamicSttStatus(testStt.getId());

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(valueOperations).get(STT_STATUS_HASH_PREFIX + testStt.getId());
        verify(sttRepository, never()).findById(anyLong()); // 캐시 히트 시 DB 조회 없음
    }

    @Test
    @DisplayName("성공: 캐시에서 동적 STT 상태 조회 (캐시 미스 -> DB 조회)")
    void getDynamicSttStatus_CacheMiss_Success() {
        // given
        when(valueOperations.get(STT_STATUS_HASH_PREFIX + testStt.getId())).thenReturn(null);
        when(sttRepository.findById(anyLong())).thenReturn(Optional.of(testStt));
        when(fileService.getSTTFile(anyLong())).thenReturn(testAudioFile);

        // when
        STTDto result = sttService.getDynamicSttStatus(testStt.getId());

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(valueOperations).get(STT_STATUS_HASH_PREFIX + testStt.getId());
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
        verify(redisTemplate.opsForSet()).add(eq("stt:recording:set"), anyString());
        verify(kafkaTemplate).send(eq("stt-abnormal-termination-topic"), anyString(), anyString());
    }

    @Test
    @DisplayName("성공: STT 파일 업로드 및 번역 요청")
    void uploadAndTranslate_Success() {
        // given
        MockMultipartFile mockFile = new MockMultipartFile("audio", "audio.wav", "audio/wav", "audio data".getBytes());
        Resource mockResource = mock(Resource.class);
        when(mockFile.getResource()).thenReturn(mockResource); // getResource() mocking

        when(meetingRepository.findById(anyLong())).thenReturn(Optional.of(testMeeting));
        when(sttProvider.requestTranscription(any(Resource.class))).thenReturn(Mono.just("rid123"));
        when(sttRepository.save(any(STT.class))).thenReturn(testStt);
        when(fileService.uploadFiles(anyLong(), anyList(), any(TargetType.class))).thenReturn(Collections.singletonList(testAudioFile));

        // when
        STTDto result = sttService.uploadAndTranslate(testMeeting.getId(), mockFile);

        // then
        assertThat(result.getId()).isEqualTo(testStt.getId());
        verify(redisTemplate.opsForSet()).add(eq("stt:processing:set"), anyString());
        verify(kafkaTemplate).send(eq("stt-processing-topic"), anyString(), anyString());
    }
    
//    @Test
//    @DisplayName("성공: STT 상태 확인")
//    void checkSTTStatus_Success() {
//        // given
//        String rid = "rid123";
//        SttTranscriptionResult transcriptionResult = new SttTranscriptionResult();
//        Mono<SttTranscriptionResult> monoResult = Mono.just(transcriptionResult);
//        when(sttProvider.checkTranscriptionStatus(rid)).thenReturn(monoResult);
//
//        // when
//        Mono<SttTranscriptionResult> result = sttProvider.checkSTTStatus(rid);
//
//        // then
//        assertThat(result.block()).isEqualTo(transcriptionResult);
//    }
}
