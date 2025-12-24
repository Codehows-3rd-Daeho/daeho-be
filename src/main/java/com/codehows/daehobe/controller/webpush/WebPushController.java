package com.codehows.daehobe.controller.webpush;

import com.codehows.daehobe.dto.webpush.PushSubscriptionDto;
import com.codehows.daehobe.service.webpush.WebPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebPushController {
    private final WebPushService webPushService; // 웹 푸시 구독 정보를 저장하고 관리하는 서비스

    @PostMapping("/push/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody PushSubscriptionDto subscription, Authentication authentication) {
        //  로그인한 사용자의 ID를 가져와 구독 정보와 함께 저장.
        try {
            webPushService.saveSubscription(subscription, authentication.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
