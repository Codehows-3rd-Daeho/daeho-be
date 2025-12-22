package com.codehows.daehobe.dto.webpush;

import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.entity.notification.Notification;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

// 알림 조회용 dto
@Getter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class NotificationResponseDto {
    private String senderName; // 보낸사람
    private String message;
    private String forwardUrl;
//    private boolean read;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;

    public static NotificationResponseDto fromEntity(Notification entity, Member sender) {
        return NotificationResponseDto.builder()
                .senderName(sender.getName())
                .message(entity.getMessage())
                .forwardUrl(entity.getForwardUrl())
//                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
