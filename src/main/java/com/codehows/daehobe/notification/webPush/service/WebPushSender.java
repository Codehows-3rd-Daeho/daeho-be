package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;

/**
 * 웹 푸시 전송 인터페이스
 * 책임: 순수 푸시 알림 전송만 담당
 */
public interface WebPushSender {

    /**
     * 특정 회원에게 푸시 알림 전송
     *
     * @param memberId   대상 회원 ID
     * @param messageDto 알림 메시지 DTO
     */
    void sendPushNotification(String memberId, NotificationMessageDto messageDto);
}
