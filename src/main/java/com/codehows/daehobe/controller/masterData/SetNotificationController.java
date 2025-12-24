package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.SetNotificationDto;
import com.codehows.daehobe.service.masterData.SetNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SetNotificationController {
    private final SetNotificationService setNotificationService;

    @PostMapping("/admin/notificationSetting")
    public ResponseEntity<?> saveSettings(@RequestBody SetNotificationDto dto) {
        try {
            setNotificationService.saveSetting(dto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/admin/notificationSetting")
    public ResponseEntity<SetNotificationDto> getSettings() {
        SetNotificationDto settings = setNotificationService.getSetting();
        return ResponseEntity.ok(settings);
    }
}
