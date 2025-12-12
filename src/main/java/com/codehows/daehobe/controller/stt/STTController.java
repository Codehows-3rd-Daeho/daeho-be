package com.codehows.daehobe.controller.stt;


import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.service.stt.STTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/stt")
@RequiredArgsConstructor
public class STTController {

    private final STTService sttService;

    @PostMapping("/create")
    public ResponseEntity<?> createMeeting(@PathVariable Long meetingId,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles) {

        System.out.println("==============이슈 등록 시작");
        sttService.uploadSTT(meetingId, multipartFiles);

        return ResponseEntity.ok().build();

    }
}
