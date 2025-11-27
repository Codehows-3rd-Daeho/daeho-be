package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.FileSetting;
import com.codehows.daehobe.repository.masterData.FileSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class FileSettingService {
    // 행의 ID를 1L로 고정.
    private static final Long SINGLE_ROW_ID = 1L;
    // 바이트 변환 상수 (1MB)
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private final FileSettingRepository fileSettingRepository;

//    public MasterDataDto getFileSize() {
//
//    }

    @Transactional
    public void saveFileSize(MasterDataDto masterDataDto) {
        FileSetting existingSetting = fileSettingRepository.findById(SINGLE_ROW_ID).orElse(null);
        Long newSize = Long.valueOf(masterDataDto.getName());
        Long newSizeInBytes = newSize * BYTES_PER_MEGABYTE;
        // 기존 행이 있으면 업데이트
        if(existingSetting != null) {
            existingSetting.setSize(newSizeInBytes); //update 쿼리 날림
        } else{
            // 기존 행이 없으면 (새로 등록)
            FileSetting fileSetting = FileSetting.builder()
                    .id(SINGLE_ROW_ID)
                    .size(newSizeInBytes)
                    .build();

            fileSettingRepository.save(fileSetting);
        }
    }
}
