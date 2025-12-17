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

        sttService.uploadSTT(id, multipartFiles);
        return ResponseEntity.ok().build();

    }

    @GetMapping("/meeting/{id}")
    public ResponseEntity<?> getSTT(@PathVariable Long id) {
        List<STTDto> stts = sttService.getSTTById(id);

        // 데이터 없으면 404 반환
        if (stts.isEmpty()) {
            return ResponseEntity.status(404)
                    .body("해당 회의에 STT가 존재하지 않습니다.");
        }
        return ResponseEntity.ok(stts);
    }

    @PostMapping("/{id}/summary")
    public ResponseEntity<?> createSTTSummary(@PathVariable Long id,
                                       @RequestBody String content) {
        sttService.summarySTT(id, content);
        return ResponseEntity.ok().build();

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSTT(@PathVariable Long id) {
        sttService.deleteSTT(id);
        return ResponseEntity.noContent().build(); // 204 반환
    }

}
