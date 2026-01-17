package com.codehows.daehobe.stt.controller;


import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.dto.StartRecordingRequest;
import com.codehows.daehobe.stt.service.STTService;
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

    @GetMapping("/meeting/{id}")
    public ResponseEntity<List<STTDto>> getSTTs(@PathVariable Long id, Authentication authentication) {
        Long memberId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(sttService.getSTTsByMeetingId(id, memberId));
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<STTDto> getSTT(@PathVariable Long id) {
        return ResponseEntity.ok(sttService.getDynamicSttStatus(id));
    }

    @PostMapping("/recording/start")
    public ResponseEntity<STTDto> startRecording(@RequestBody StartRecordingRequest request) {
        return ResponseEntity.ok(sttService.startRecording(request.getMeetingId()));
    }

    @PostMapping("/{sttId}/chunk")
    public ResponseEntity<STTDto> uploadChunk(
            @PathVariable Long sttId,
            @RequestPart("file") MultipartFile chunk,
            @RequestPart(value = "finish", required = false) String finish
    ) {
        Boolean isFinish = Boolean.parseBoolean(finish);
        return ResponseEntity.ok(sttService.appendChunk(sttId, chunk, isFinish));
    }

    @PatchMapping("/{id}/summary")
    public ResponseEntity<?> updateSTTSummary(@PathVariable Long id, @RequestBody String content) {
        sttService.updateSummary(id, content);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSTT(@PathVariable Long id) {
        sttService.deleteSTT(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload/{id}")
    public ResponseEntity<STTDto> createSTT(@PathVariable Long id,
                                                  @RequestPart(value = "file", required = false) MultipartFile multipartFiles) {
        return ResponseEntity.ok(sttService.uploadAndTranslate(id, multipartFiles));
    }

    @PostMapping("/{sttId}/recording/finish")
    public ResponseEntity<STTDto> finishRecording(@PathVariable Long sttId) {
        return ResponseEntity.ok(sttService.startTranslateForRecorded(sttId));
    }
}
