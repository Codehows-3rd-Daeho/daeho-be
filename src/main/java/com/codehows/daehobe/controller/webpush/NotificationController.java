package com.codehows.daehobe.controller.webpush;

import com.codehows.daehobe.dto.webpush.NotificationResponseDto;
import com.codehows.daehobe.service.webpush.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    // 내 알림 조회 (5개씩)
    @GetMapping("/notifications")
    public ResponseEntity<?> getMyNotifications(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "5") int size,
                                                Authentication authentication) {
        try {
            Page<NotificationResponseDto> dtos = notificationService.getMyNotifications(
                    Long.valueOf(authentication.getName()),
                    page,
                    size);
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
