package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.service.masterData.JobPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobPositionController {
    private final JobPositionService jobPositionService;

    @GetMapping("/jobPosition")
    public ResponseEntity<?> getJobPosition() {
        try {
            List<MasterDataDto> jobPositions = jobPositionService.findAll();
            return ResponseEntity.ok(jobPositions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("직급 조회 중 오류 발생");
        }
    }

}
