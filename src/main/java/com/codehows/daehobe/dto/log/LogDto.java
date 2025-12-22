package com.codehows.daehobe.dto.log;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.log.Log;
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

    private Long targetId;

    private TargetType targetType;

    private ChangeType changeType;

    private String message;

    private String updateField;

    private LocalDateTime createTime;

    private Long createBy;

    public static LogDto fromEntity(Log log) {
        return LogDto.builder()
                .id(log.getId())
                .targetId(log.getTargetId())
                .targetType(log.getTargetType())
                .changeType(log.getChangeType())
                .message(log.getMessage())
                .updateField(log.getUpdateField())
                .createTime(log.getCreatedAt())
                .createBy(log.getCreatedBy())
                .build();
    }
}
