package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.service.masterData.JobPositionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobPositionController {
    private final JobPositionService jobPositionService;

    @GetMapping("/masterData/jobPosition")
    public ResponseEntity<?> getJobPosition() {
        try {
            List<MasterDataDto> jobPositions = jobPositionService.findAll();
            return ResponseEntity.ok(jobPositions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("직급 조회 중 오류 발생");
        }
    }

    @PostMapping("/admin/jobPosition")
    public ResponseEntity<?> createPosition(@RequestBody MasterDataDto masterDataDto) {
        try {
            JobPosition pos = jobPositionService.createPos(masterDataDto);
            return ResponseEntity.ok(pos);
        } catch (IllegalArgumentException e) {
            // 중복
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("직급 등록 중 오류 발생");
        }
    }

    @DeleteMapping("/admin/jobPosition/{id}")
    public ResponseEntity<?> deletePosition(@PathVariable Long id) {
        try {
            jobPositionService.deletePos(id);
            return ResponseEntity.noContent().build(); // 204 No Content 반환
        }  catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("직급 삭제 중 오류 발생");
        }
    }

}
