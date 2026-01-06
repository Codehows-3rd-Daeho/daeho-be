package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.service.masterData.DepartmentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @GetMapping("/masterData/department")
    public ResponseEntity<?> getDpt() {
        try {
            List<MasterDataDto> departments = departmentService.findAll();
            return ResponseEntity.ok(departments);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("부서 조회 중 오류 발생");
        }
    }

    @PostMapping("/admin/department")
    public ResponseEntity<?> createDpt(@RequestBody MasterDataDto masterDataDto) {
        try {
            Department dpt = departmentService.createDpt(masterDataDto);
            return ResponseEntity.ok(dpt);
        } catch (IllegalArgumentException e) {
            // 중복
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("부서 등록 중 오류 발생");
        }
    }

    @DeleteMapping("/admin/department/{id}")
    public ResponseEntity<?> deleteDpt(@PathVariable Long id) {
        try {
            departmentService.deleteDpt(id); // 성공 시 204 No Content 반환
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            // 404 Not Found 상태 코드 반환
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("삭제하려는 부서를 찾을 수 없습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            // 그 외
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("부서 삭제 중 알 수 없는 오류 발생");
        }
    }

    @PatchMapping("/admin/department/{id}")
    public ResponseEntity<?> updateDpt(@PathVariable Long id, @RequestBody MasterDataDto masterDataDto) {
        try {
            Department department = departmentService.updateDpt(id, masterDataDto);
            return ResponseEntity.ok(department);
        } catch (DataIntegrityViolationException e) {
            // DB unique 제약조건 위반
            return ResponseEntity.badRequest().body("이미 등록된 부서가 있습니다.");

        }catch (IllegalArgumentException e) {
            // 중복
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("부서 수정 중 오류 발생");
        }
    }
}
