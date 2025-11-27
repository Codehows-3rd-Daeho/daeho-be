package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.exception.ReferencedEntityException;
import com.codehows.daehobe.service.masterData.JobPositionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
            Long posId = jobPositionService.createPos(masterDataDto);
            return ResponseEntity.ok(posId);
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
        }
        catch (ReferencedEntityException e) {
            e.printStackTrace();
            // 409 Conflict 상태 코드와 예외 메시지 반환
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            // 404 Not Found
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("삭제하려는 직급을 찾을 수 없습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            // 그 외
            return ResponseEntity.status(500).body("직급 삭제 중 오류 발생");
        }
    }

}
