package com.codehows.daehobe.dto.log;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.log.Log;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class LogDto {
    private Long id;

    private String title;

    private Long targetId;

    private TargetType targetType;

    private Long parentId;

    private String parentType;

    private ChangeType changeType;

    private String message;

    private String updateField;

    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createTime;

    // 사용자
    private String memberName;

    public static LogDto fromEntity(Log log) {
        return LogDto.builder()
                .id(log.getId())
                .targetId(log.getTargetId())
                .parentId(log.getParentId())
                .parentType(log.getParentType())
                .title(log.getTitle())
                .targetType(log.getTargetType())
                .changeType(log.getChangeType())
                .message(log.getMessage())
                .updateField(log.getUpdateField())
                .createTime(log.getCreatedAt())
                .memberName(log.getMemberName())
                .build();
    }
}
