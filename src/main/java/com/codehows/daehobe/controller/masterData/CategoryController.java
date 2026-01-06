package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.service.masterData.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping("/masterData/category")
    public ResponseEntity<?> getCategory() {
        try {
            List<MasterDataDto> categories = categoryService.findAll();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("카테고리 조회 중 오류 발생");
        }
    }

    @PostMapping("/admin/category")
    public ResponseEntity<?> createCategory(@RequestBody MasterDataDto masterDataDto) {
        try {
            Category category = categoryService.createCategory(masterDataDto);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            // 중복
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("카테고리 등록 중 오류 발생");
        }
    }

    @DeleteMapping("/admin/category/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.noContent().build(); // 204 No Content 반환
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("카테고리 삭제 중 오류 발생");
        }
    }

    @PatchMapping("/admin/category/{id}")
    public ResponseEntity<?> createCategory(@PathVariable Long id, @RequestBody MasterDataDto masterDataDto) {
        try {
            Category category = categoryService.updateCategory(id, masterDataDto);
            return ResponseEntity.ok(category);
        }  catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("카테고리 수정 중 오류 발생");
        }
    }
}
