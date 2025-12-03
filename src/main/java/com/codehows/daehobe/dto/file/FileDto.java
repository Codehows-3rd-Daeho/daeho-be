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




}
