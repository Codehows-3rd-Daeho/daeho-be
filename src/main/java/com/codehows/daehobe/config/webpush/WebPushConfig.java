/**
 * @file WebPushConfig.java
 * @description Web Push 프로토콜 관련 설정을 담당하는 클래스입니다.
 *              VAPID(Voluntary Application Server Identification) 키를 사용하여 푸시 서비스 인증을 처리하고,
 *              푸시 메시지 전송을 위한 {@link PushService} 빈을 생성합니다.
 */

package com.codehows.daehobe.config.webpush;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * @class WebPushConfig
 * @description Web Push 프로토콜 관련 설정을 정의하는 클래스입니다.
 *              VAPID 키를 사용하여 푸시 서비스 인증을 처리하고,
 *              푸시 메시지 전송을 위한 `PushService` 빈을 생성합니다.
 */
@Configuration // Spring 설정 클래스임을 나타냅니다.
public class WebPushConfig {

    /**
     * VAPID 공개 키입니다.
     * `application.properties` 파일에서 `vapid.public.key` 속성 값을 주입받습니다.
     * 이 키는 클라이언트(프론트엔드)에 제공되어 푸시 구독 시 사용됩니다.
     * 푸시 서비스는 이 키를 사용하여 어떤 애플리케이션 서버가 메시지를 보냈는지 식별합니다.
     */
    @Value("${vapid.public.key}")
    private String vapidPublicKey;

    /**
     * VAPID 개인 키입니다.
     * `application.properties` 파일에서 `vapid.private.key` 속성 값을 주입받습니다.
     * 이 키는 서버에만 안전하게 보관되어야 하며, 푸시 메시지를 서명하는 데 사용됩니다.
     * 푸시 서비스는 이 서명을 공개 키로 검증하여 메시지가 인증된 서버로부터 왔는지 확인합니다.
     */
    @Value("${vapid.private.key}")
    private String vapidPrivateKey;

    /**
     * @method pushService
     * @description `nl.martijndwars:web-push` 라이브러리의 핵심 서비스인 {@link PushService}를 Spring 빈으로 등록합니다.
     *              이 서비스는 VAPID 키를 사용하여 푸시 요청을 암호화하고 서명한 후, 외부 푸시 서비스로 전송하는 역할을 합니다.
     * @returns {PushService} 초기화된 PushService 객체
     * @throws {GeneralSecurityException} 암호화 관련 보안 예외 발생 시
     */
    @Bean // Spring 컨테이너에 빈으로 등록합니다.
    public PushService pushService() throws GeneralSecurityException {
        // BouncyCastle은 자바의 기본 암호화 기능(JCA)을 확장하는 라이브러리입니다.
        // web-push 라이브러리는 VAPID 서명을 위해 BouncyCastle의 암호화 알고리즘(Elliptic Curve)을 사용합니다.
        // Security.getProvider(...)를 통해 BouncyCastleProvider가 이미 등록되어 있는지 확인하고,
        // 등록되어 있지 않으면 수동으로 추가해줍니다.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // VAPID 공개 키와 개인 키를 사용하여 PushService를 초기화하고 빈으로 반환합니다.
        return new PushService(vapidPublicKey, vapidPrivateKey);
    }
}