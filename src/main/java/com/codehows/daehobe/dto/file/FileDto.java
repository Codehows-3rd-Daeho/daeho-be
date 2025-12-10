package com.codehows.daehobe.dto.file;

import com.codehows.daehobe.entity.file.File;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.codehows.daehobe.service.issue.IssueService.dateFormatter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDto {
    private Long fileId;
    private String path;
    private String originalName;
    private String size;
    private String createdAt;

    //Entity -> Dto
    public static FileDto fromEntity(File file) {
        return FileDto.builder()
                .fileId(file.getFileId())
                .path(file.getPath())
                .originalName(file.getOriginalName())
                .size(formatSize(file.getSize()))
                .createdAt(file.getCreatedAt().format(dateFormatter))
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
