package com.codehows.daehobe.logging.controller;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.logging.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogController {
    private final LogService logService;

    @GetMapping("/issue/{id}/log")
    public ResponseEntity<?> issueLog(@PathVariable Long id,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "5") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            return ResponseEntity.ok(logService.getIssueLogs(id, pageable));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 로그 조회 중 오류 발생");
        }
    }

    @GetMapping("/meeting/{id}/log")
    public ResponseEntity<?> meetingLog(@PathVariable Long id,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "5") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            return ResponseEntity.ok(logService.getMeetingLogs(id, pageable));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회의 로그 조회 중 오류 발생");
        }
    }

    @GetMapping("/admin/log")
    public ResponseEntity<?> allLog(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) TargetType targetType) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            return ResponseEntity.ok(logService.getAllLogs(targetType, pageable));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("전체 로그 조회 중 오류 발생");
        }
    }

}
