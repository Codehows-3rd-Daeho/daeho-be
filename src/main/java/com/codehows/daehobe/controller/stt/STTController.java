package com.codehows.daehobe.controller.stt;


import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.service.stt.STTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/stt")
@RequiredArgsConstructor
public class STTController {

    private final STTService sttService;

    @PostMapping("/meeting/{id}")
    public ResponseEntity<?> createSTT(@PathVariable Long id,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles) {

        System.out.println("==============stt 등록 시작==============");
        sttService.uploadSTT(id, multipartFiles);

        return ResponseEntity.ok().build();

    }

    @GetMapping("/meeting/{id}")
    public ResponseEntity<?> getSTT(@PathVariable Long id) {
        System.out.println("==============stt 조회 시작==========");
        List<STTDto> stts = sttService.getSTTById(id);


        if (stts.isEmpty()) {
            // 데이터 없으면 404 반환
            return ResponseEntity.status(404)
                    .body("해당 회의에 STT가 존재하지 않습니다.");
        }

        return ResponseEntity.ok(stts);
    }


}
