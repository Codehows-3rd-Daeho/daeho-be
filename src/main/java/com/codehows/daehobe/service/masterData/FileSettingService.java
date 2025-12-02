package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.AllowedExtension;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.MaxFileSize;
import com.codehows.daehobe.repository.masterData.AllowedExtensionRepository;
import com.codehows.daehobe.repository.masterData.MaxFileSizeRepository;
import jakarta.persistence.EntityExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class FileSettingService {
    // 행의 ID를 1L로 고정.
    private static final Long ROW_ID = 1L;
    // 바이트 변환 상수 (1MB)
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private final MaxFileSizeRepository maxFileSizeRepository;
    private final AllowedExtensionRepository allowedExtensionRepository;

    public MasterDataDto getFileSize() {
        MaxFileSize maxFileSize = maxFileSizeRepository.findById(ROW_ID).orElse(null);
        if (maxFileSize == null) {
            return new MasterDataDto(ROW_ID, "");
        } else {
            return new MasterDataDto(ROW_ID, maxFileSize.getSizeByte().toString());
        }
    }

    @Transactional
    public void saveFileSize(MasterDataDto masterDataDto) {
        Long newSize = Long.valueOf(masterDataDto.getName());
        Long newSizeInBytes = newSize * BYTES_PER_MEGABYTE;

        // 기존 행 조회
        MaxFileSize existing = maxFileSizeRepository.findById(ROW_ID).orElse(null);

        if (existing != null) {
            existing.updateSize(newSizeInBytes);
        } else {
            // 없으면 새 엔티티 생성
            MaxFileSize created = new MaxFileSize(ROW_ID, newSizeInBytes);
            maxFileSizeRepository.save(created);
        }
    }

    public List<MasterDataDto> getExtensions() {
        List<AllowedExtension> extensionList = allowedExtensionRepository.findAll();

        List<MasterDataDto> dtoList = new ArrayList<>();
        for (AllowedExtension extension : extensionList) {
            dtoList.add(new MasterDataDto(extension.getId(), extension.getName()));
        }
        return dtoList;
    }

    @Transactional
    public AllowedExtension saveExtension(MasterDataDto masterDataDto) {
        String extensionName = masterDataDto.getName();

        // 중복 체크
        if (allowedExtensionRepository.existsByName(extensionName)) {
            throw new IllegalArgumentException("이미 존재하는 확장자입니다: " + extensionName);
        }

        AllowedExtension extension = AllowedExtension.builder()
                .name(extensionName)
                .build();

        try {
            return allowedExtensionRepository.save(extension);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteExtension(Long id) {
        AllowedExtension ext =  allowedExtensionRepository.findById(id).orElseThrow(EntityExistsException::new);
        allowedExtensionRepository.delete(ext);
    }


}
