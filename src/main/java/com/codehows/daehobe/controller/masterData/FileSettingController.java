package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.AllowedExtension;
import com.codehows.daehobe.service.masterData.FileSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FileSettingController {
    private final FileSettingService fileSettingService;

    // 파일 용량
    @GetMapping("/file/size")
    public ResponseEntity<?> getFileSize() {
        try {
            MasterDataDto dto = fileSettingService.getFileSize();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("파일 용량 불러오기 중 오류 발생");
        }
    }

    @PostMapping("/admin/file/size")
    public ResponseEntity<?> saveFileSize(@RequestBody MasterDataDto masterDataDto) {
        try {
            fileSettingService.saveFileSize(masterDataDto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("파일 용량 등록 중 오류 발생");
        }
    }

    // 파일 확장자
    @GetMapping("/file/extension")
    public ResponseEntity<?> getExtensions() {
        try {
            List<MasterDataDto> dtoList = fileSettingService.getExtensions();
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("파일 확장자 불러오기 중 오류 발생");
        }
    }

    @PostMapping("/admin/file/extension")
    public ResponseEntity<?> saveExtension(@RequestBody MasterDataDto masterDataDto) {
        try {
            AllowedExtension extension = fileSettingService.saveExtension(masterDataDto);
            return ResponseEntity.ok(extension);
        } catch (IllegalArgumentException e) {
            // 중복
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("파일 확장자 등록 중 오류 발생");
        }
    }

    @DeleteMapping("/admin/file/extension/{id}")
    public ResponseEntity<?> deleteExtension(@PathVariable Long id) {
        try {
            fileSettingService.deleteExtension(id);
            return ResponseEntity.noContent().build(); // 204 No Content 반환
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("확장자 삭제 중 오류 발생");
        }
    }
}
