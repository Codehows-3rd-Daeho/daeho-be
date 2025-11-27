package com.codehows.daehobe.dto.file;

import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.constant.TargetType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FileDto {
    private String path;
    private String originalName;
    private String savedName;
    private Long size;
    private Long targetId;
    private TargetType targetType;


    // DTO -> Entity 변환
    public File toEntity() {
        return File.builder()
                .path(this.path)
                .originalName(this.originalName)
                .savedName(this.savedName)
                .size(this.size)
                .targetId(this.targetId)
                .targetType(this.targetType)
                .build();
    }


}
