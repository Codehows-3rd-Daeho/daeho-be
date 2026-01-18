package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.AllowedExtension;
import com.codehows.daehobe.masterData.entity.MaxFileSize;
import com.codehows.daehobe.masterData.repository.AllowedExtensionRepository;
import com.codehows.daehobe.masterData.repository.MaxFileSizeRepository;
import jakarta.persistence.EntityExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileSettingServiceTest {

    @Mock
    private MaxFileSizeRepository maxFileSizeRepository;

    @Mock
    private AllowedExtensionRepository allowedExtensionRepository;

    @InjectMocks
    private FileSettingService fileSettingService;

    private static final Long ROW_ID = 1L;

    @Test
    @DisplayName("성공: 파일 크기 설정 조회 (기존 설정 있음)")
    void getFileSize_Existing() {
        // given
        MaxFileSize maxFileSize = new MaxFileSize(ROW_ID, 1024L * 1024L);
        when(maxFileSizeRepository.findById(ROW_ID)).thenReturn(Optional.of(maxFileSize));

        // when
        MasterDataDto result = fileSettingService.getFileSize();

        // then
        assertThat(result.getName()).isEqualTo(String.valueOf(1024L * 1024L));
    }

    @Test
    @DisplayName("성공: 파일 크기 설정 조회 (기존 설정 없음)")
    void getFileSize_NotExisting() {
        // given
        when(maxFileSizeRepository.findById(ROW_ID)).thenReturn(Optional.empty());

        // when
        MasterDataDto result = fileSettingService.getFileSize();

        // then
        assertThat(result.getName()).isEqualTo("");
    }

    @Test
    @DisplayName("성공: 파일 크기 설정 업데이트")
    void saveFileSize_Update() {
        // given
        MasterDataDto dto = new MasterDataDto(ROW_ID, "2.5"); // 2.5 MB
        MaxFileSize existingSize = new MaxFileSize(ROW_ID, 1024L * 1024L);
        when(maxFileSizeRepository.findById(ROW_ID)).thenReturn(Optional.of(existingSize));
        
        // when
        fileSettingService.saveFileSize(dto);

        // then
        assertThat(existingSize.getSizeByte()).isEqualTo(Math.round(2.5 * 1024 * 1024));
        verify(maxFileSizeRepository, never()).save(any(MaxFileSize.class)); // update should not call save explicitly
    }

    @Test
    @DisplayName("성공: 파일 크기 설정 생성")
    void saveFileSize_Create() {
        // given
        MasterDataDto dto = new MasterDataDto(ROW_ID, "3"); // 3 MB
        when(maxFileSizeRepository.findById(ROW_ID)).thenReturn(Optional.empty());
        
        // when
        fileSettingService.saveFileSize(dto);

        // then
        verify(maxFileSizeRepository).save(any(MaxFileSize.class));
    }

    @Test
    @DisplayName("성공: 모든 확장자 조회")
    void getExtensions_Success() {
        // given
        List<AllowedExtension> extensions = Arrays.asList(
                AllowedExtension.builder().id(1L).name("txt").build(),
                AllowedExtension.builder().id(2L).name("pdf").build()
        );
        when(allowedExtensionRepository.findAll()).thenReturn(extensions);
        
        // when
        List<MasterDataDto> result = fileSettingService.getExtensions();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("txt");
    }
    
    @Test
    @DisplayName("성공: 확장자 목록이 비어있을 때 모든 확장자 조회")
    void getExtensions_EmptyList() {
        // given
        when(allowedExtensionRepository.findAll()).thenReturn(new ArrayList<>());
        
        // when
        List<MasterDataDto> result = fileSettingService.getExtensions();

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("성공: 확장자 저장")
    void saveExtension_Success() {
        // given
        MasterDataDto dto = new MasterDataDto(null, "jpg");
        when(allowedExtensionRepository.existsByName("jpg")).thenReturn(false);
        when(allowedExtensionRepository.save(any(AllowedExtension.class)))
                .thenReturn(AllowedExtension.builder().id(1L).name("jpg").build());

        // when
        AllowedExtension result = fileSettingService.saveExtension(dto);

        // then
        assertThat(result.getName()).isEqualTo("jpg");
        verify(allowedExtensionRepository).save(any(AllowedExtension.class));
    }

    @Test
    @DisplayName("실패: 중복 확장자 저장")
    void saveExtension_Duplicate() {
        // given
        MasterDataDto dto = new MasterDataDto(null, "png");
        when(allowedExtensionRepository.existsByName("png")).thenReturn(true);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> fileSettingService.saveExtension(dto));
        verify(allowedExtensionRepository, never()).save(any(AllowedExtension.class));
    }

    @Test
    @DisplayName("성공: 확장자 삭제")
    void deleteExtension_Success() {
        // given
        Long extId = 1L;
        AllowedExtension extension = AllowedExtension.builder().id(extId).name("gif").build();
        when(allowedExtensionRepository.findById(extId)).thenReturn(Optional.of(extension));
        doNothing().when(allowedExtensionRepository).delete(extension);

        // when
        fileSettingService.deleteExtension(extId);

        // then
        verify(allowedExtensionRepository).delete(extension);
    }

    @Test
    @DisplayName("실패: 삭제할 확장자 없음")
    void deleteExtension_NotFound() {
        // given
        Long extId = 99L;
        when(allowedExtensionRepository.findById(extId)).thenReturn(Optional.empty());
        
        // when & then
        assertThrows(EntityExistsException.class, () -> fileSettingService.deleteExtension(extId));
    }
}
