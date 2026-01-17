package com.codehows.daehobe.notification.dto;

import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

// 알림 조회용 dto
@Getter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class NotificationResponseDto {
    private Long id;
    private String senderName; // 보낸사람
    private String message;
    private String forwardUrl;
    @JsonProperty("isRead")
    private boolean read;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;

    public static NotificationResponseDto fromEntity(Notification entity, Member sender) {
        return NotificationResponseDto.builder()
                .id(entity.getId())
                .senderName(sender.getName())
                .message(entity.getMessage())
                .forwardUrl(entity.getForwardUrl())
                .read(entity.getIsRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
