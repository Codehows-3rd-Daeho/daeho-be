package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.service.masterData.FileSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FileSettingController {
    private final FileSettingService fileSettingService;

//    @GetMapping("/admin/file/size")
//    public ResponseEntity<?> getFileSize() {
//        try {
//            MasterDataDto dto = fileSettingService.getFileSize();
//            return ResponseEntity.ok(dto);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("파일 용량 불러오기 중 오류 발생");
//        }
//    }

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
}
