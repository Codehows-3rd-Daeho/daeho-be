package com.codehows.daehobe.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedNotificationDto {
    private String memberId;
    private String message;
    private String url;
    private int statusCode;
    private String errorMessage;
    private LocalDateTime failedAt;
    private int retryCount;

    public static FailedNotificationDto of(String memberId, NotificationMessageDto messageDto,
                                           int statusCode, String errorMessage) {
        return FailedNotificationDto.builder()
                .memberId(memberId)
                .message(messageDto.getMessage())
                .url(messageDto.getUrl())
                .statusCode(statusCode)
                .errorMessage(errorMessage)
                .failedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    /**
     * retryCount를 1 증가시킨 새 인스턴스 반환
     */
    public FailedNotificationDto withIncrementedRetryCount() {
        return FailedNotificationDto.builder()
                .memberId(this.memberId)
                .message(this.message)
                .url(this.url)
                .statusCode(this.statusCode)
                .errorMessage(this.errorMessage)
                .failedAt(this.failedAt)
                .retryCount(this.retryCount + 1)
                .build();
    }
}
