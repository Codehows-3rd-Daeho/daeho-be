package com.codehows.daehobe.notification.controller;

import com.codehows.daehobe.notification.dto.NotificationResponseDto;
import com.codehows.daehobe.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<?> readNotification(@PathVariable Long id) {
        try {
            notificationService.readNotification(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<?> getUnreadNotificationCount(Authentication authentication) {
        try {
            int count = notificationService.getUnreadCount(Long.valueOf(authentication.getName()));
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
