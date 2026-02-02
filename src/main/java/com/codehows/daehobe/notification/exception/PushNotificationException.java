package com.codehows.daehobe.notification.exception;

/**
 * 푸시 알림 전송 실패 시 발생하는 예외
 */
public class PushNotificationException extends RuntimeException {

    private final int statusCode;

    public PushNotificationException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public PushNotificationException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public PushNotificationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public PushNotificationException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
