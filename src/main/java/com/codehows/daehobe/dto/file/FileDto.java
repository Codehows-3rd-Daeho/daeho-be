package com.codehows.daehobe.dto.file;

import com.codehows.daehobe.entity.file.File;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDto {
    private Long fileId;
    private String path;
    private String originalName;
    private Long size;
    private String createdAt;

    //Entity -> Dto
    public static FileDto fromEntity(File file) {
        return FileDto.builder()
                .fileId(file.getFileId())
                .path(file.getPath())
                .originalName(file.getOriginalName())
                .size(file.getSize())
                .createdAt(String.valueOf(file.getCreatedAt()))
                .build();
    }
}
