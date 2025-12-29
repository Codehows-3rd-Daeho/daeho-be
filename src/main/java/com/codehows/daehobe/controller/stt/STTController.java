package com.codehows.daehobe.controller.stt;


import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.StartRecordingRequest;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.stt.STTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stt")
@RequiredArgsConstructor
public class STTController {
    private final STTService sttService;

    @PostMapping("/meeting/{id}")
    public ResponseEntity<?> createSTT(@PathVariable Long id,
            @RequestPart(value = "file", required = false) MultipartFile multipartFiles) {

        STTDto stt = sttService.uploadSTT(id, multipartFiles);
        return ResponseEntity.ok(stt);

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
    public ResponseEntity<?> uploadChunk(@PathVariable Long sttId, @RequestParam("file") MultipartFile chunk) {
        STTDto sttDto = sttService.appendChunk(sttId, chunk);
        return ResponseEntity.ok(sttDto);
    }

    @PostMapping("/{sttId}/recording/finish")
    public ResponseEntity<STTDto> finishRecording(@PathVariable Long sttId) {
        try{
            STTDto sttDto = sttService.finishRecording(sttId);
            return ResponseEntity.ok(sttDto);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().build();
        }
    }
}
