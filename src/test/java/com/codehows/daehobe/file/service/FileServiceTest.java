package com.codehows.daehobe.file.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.repository.FileRepository;
import com.codehows.daehobe.common.utils.AudioProcessor;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private AudioProcessor audioProcessor;

    @InjectMocks
    private FileService fileService;

    @BeforeEach
    void setUp() {
        // @Value 필드 수동 주입
        ReflectionTestUtils.setField(fileService, "fileLocation", "/tmp/daehobe_test");
    }

    @Test
    @DisplayName("성공: 파일 레코드 생성")
    void createFile_Success() {
        // given
        String fileName = "test.txt";
        Long targetId = 1L;
        TargetType targetType = TargetType.ISSUE;
        File savedFile = File.builder().fileId(1L).originalName(fileName).build();
        when(fileRepository.save(any(File.class))).thenReturn(savedFile);

        // when
        File result = fileService.createFile(fileName, targetId, targetType);

        // then
        assertThat(result.getOriginalName()).isEqualTo(fileName);
        verify(fileRepository).save(any(File.class));
    }

    @Test
    @DisplayName("성공: 이슈 파일 목록 조회")
    void getIssueFiles_Success() {
        // given
        Long issueId = 1L;
        File file = File.builder().fileId(1L).originalName("issue_file.txt").path("/file/issue_file.txt").size(1024L).build();
        when(fileRepository.findByTargetIdAndTargetType(issueId, TargetType.ISSUE)).thenReturn(Collections.singletonList(file));
        
        // when
        List<FileDto> result = fileService.getIssueFiles(issueId);
        
        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalName()).isEqualTo("issue_file.txt");
    }
    
    @Test
    @DisplayName("성공: 멤버 프로필 파일 조회")
    void findFirstByTargetIdAndTargetType_Success() {
        // given
        Long memberId = 1L;
        File file = File.builder().fileId(1L).originalName("profile.jpg").build();
        when(fileRepository.findFirstByTargetIdAndTargetType(memberId, TargetType.MEMBER)).thenReturn(Optional.of(file));
        
        // when
        File result = fileService.findFirstByTargetIdAndTargetType(memberId, TargetType.MEMBER);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalName()).isEqualTo("profile.jpg");
    }

    @Test
    @DisplayName("성공: 멤버 프로필 파일 조회 (파일 없음)")
    void findFirstByTargetIdAndTargetType_NotFound() {
        // given
        Long memberId = 1L;
        when(fileRepository.findFirstByTargetIdAndTargetType(memberId, TargetType.MEMBER)).thenReturn(Optional.empty());
        
        // when
        File result = fileService.findFirstByTargetIdAndTargetType(memberId, TargetType.MEMBER);
        
        // then
        assertThat(result).isNull();
    }
}
