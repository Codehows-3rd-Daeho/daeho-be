package com.codehows.daehobe.controller.stt;


import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.StartRecordingRequest;
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

    @GetMapping("/meeting/{id}")
    public ResponseEntity<?> getSTTs(@PathVariable Long id, Authentication authentication) {
        Long memberId = Long.valueOf(authentication.getName());
        List<STTDto> stts = sttService.getSTTsByMeetingId(id, memberId);
        if (stts.isEmpty())
            return ResponseEntity.status(404).body("해당 회의에 STT가 존재하지 않습니다.");
        return ResponseEntity.ok(stts);
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> getSTT(@PathVariable Long id) {
        try{
            STTDto res = sttService.getDynamicSttStatus(id);
            return ResponseEntity.ok(res);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/upload/{id}")
    public ResponseEntity<?> createSTT(@PathVariable Long id,
                                       @RequestPart(value = "file", required = false) MultipartFile multipartFiles) {
        try{
            STTDto res = sttService.uploadAndTranslate(id, multipartFiles);
            return ResponseEntity.ok(res);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/summary")
    public ResponseEntity<?> updateSTTSummary(@PathVariable Long id, @RequestBody String content) {
        sttService.updateSummary(id, content);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSTT(@PathVariable Long id) {
        sttService.deleteSTT(id);
        return ResponseEntity.noContent().build(); // 204 반환
    }

    @PostMapping("/recording/start")
    public ResponseEntity<?> startRecording(@RequestBody StartRecordingRequest request) {
        STTDto sttDto = sttService.startRecording(request.getMeetingId());
        return ResponseEntity.ok(sttDto);
    }

    @PostMapping("/{sttId}/chunk")
    public ResponseEntity<?> uploadChunk(
            @PathVariable Long sttId,
            @RequestPart("file") MultipartFile chunk,
            @RequestPart(value = "finish", required = false) String finish
    ) {
        Boolean isFinish = Boolean.parseBoolean(finish);
        STTDto sttDto = sttService.appendChunk(sttId, chunk, isFinish);
        return ResponseEntity.ok(sttDto);
    }

    @PostMapping("/{sttId}/recording/finish")
    public ResponseEntity<?> finishRecording(@PathVariable Long sttId) {
        try{
            STTDto sttDto = sttService.startTranslateForRecorded(sttId);
            return ResponseEntity.ok(sttDto);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
