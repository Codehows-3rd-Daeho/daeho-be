package com.codehows.daehobe.notification.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * @class PushSubscriptionDto
 * @description 웹 푸시 서비스에 구독하기 위해 클라이언트로부터 전송되는 정보를 캡슐화하는 DTO입니다.
 *              푸시 서비스 엔드포인트, 사용자 ID, 그리고 암호화 키 정보를 포함합니다.
 */
@Setter
@Getter
public class PushSubscriptionDto {

    /**
     * 푸시 서비스 엔드포인트 URL입니다.
     * 이 URL로 푸시 메시지를 전송하면 클라이언트 브라우저에 알림이 전달됩니다.
     */
    private String endpoint;

    /**
     * 푸시 알림을 받을 사용자의 고유 ID입니다.
     * 이 ID를 통해 특정 사용자에게 알림을 보낼 수 있습니다.
     */
    private String memberId;

    /**
     * 푸시 구독과 관련된 암호화 키 정보를 담는 중첩 클래스입니다.
     * 이 키들은 푸시 메시지를 암호화하고 복호화하는 데 사용됩니다.
     */
    private Keys keys;

    /**
     * @class Keys
     * @description 푸시 구독에 필요한 암호화 키 정보를 담는 중첩 클래스입니다.
     */
    @Getter
    @Setter
    public static class Keys {
        /**
         * P-256 타원 곡선 Diffie-Hellman 공개 키입니다.
         * 이 키는 푸시 메시지를 암호화하는 데 사용됩니다.
         */
        private String p256dh;
        /**
         * 인증 비밀 키입니다.
         * 이 키는 푸시 메시지의 무결성을 검증하는 데 사용됩니다.
         */
        private String auth;
    }
}