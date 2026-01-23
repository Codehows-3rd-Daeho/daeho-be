package com.codehows.daehobe.file.dto;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.entity.File;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileDto {
    private Long fileId;
    private String originalName;
    private String savedName;
    private String path;
    private String size;
    private Long targetId;
    private TargetType targetType;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;
    private String createdBy;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime updatedAt;

    public static FileDto fromEntity(File file) {
        return FileDto.builder()
                .fileId(file.getFileId())
                .originalName(file.getOriginalName())
                .savedName(file.getSavedName())
                .path(file.getPath())
                .size(formatSize(file.getSize()))
                .targetId(file.getTargetId())
                .targetType(file.getTargetType())
                .createdAt(file.getCreatedAt())
                .createdBy(String.valueOf(file.getCreatedBy()))
                .updatedAt(file.getUpdatedAt())
                .build();
    }

    // 바이트 → KB/MB 변환 함수
    private static String formatSize(long size) {
        double kb = size / 1024.0;
        double mb = size / (1024.0 * 1024.0);
        double gb = size / (1024.0 * 1024.0 * 1024.0);

        if (gb >= 1) return String.format("%.2f GB", gb);
        if (mb >= 1) return String.format("%.2f MB", mb);
        if (kb >= 1) return String.format("%.2f KB", kb);

        return size + " B";   // 1KB 미만일 경우
    }

}
