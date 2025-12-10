package com.codehows.daehobe.controller.meeting;

import com.codehows.daehobe.dto.meeting.MeetingDtlDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.service.meeting.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/create")
    public ResponseEntity<?> createMeeting(
            @RequestPart("data") MeetingDto meetingDto,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles) {

        System.out.println("==============이슈 등록 시작");
        meetingService.createMeeting(meetingDto, multipartFiles);

        return ResponseEntity.ok().build();

    }

    // 회의 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getMeetingDtl(@PathVariable Long id, Authentication authentication) {
        try {
            Long memberId = Long.valueOf(authentication.getName());
            MeetingDtlDto meetingDtlDto = meetingService.getMeetingDtl(id, memberId);
            return ResponseEntity.ok(meetingDtlDto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    //참여자 확인 미확인 업데이트
    @PutMapping("/{id}/readStatus")
    public ResponseEntity<?> updateReadStatus(@PathVariable Long id, Authentication authentication) {
        try {
            Long memberId = Long.valueOf(authentication.getName());
            meetingService.updateReadStatus(id, memberId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMeeting(@PathVariable Long id) {
        try {
            meetingService.deleteMeeting(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회의 삭제 중 오류 발생");
        }
    }

    @PostMapping("/{id}/meetingMinutes")
    public ResponseEntity<?> saveMeetingMinutes(@PathVariable Long id,
                                                @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles) {
        try {
            meetingService.saveMeetingMinutes(id, multipartFiles);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{meetingId}/meetingMinutes/{fileId}")
    public ResponseEntity<?> deleteMeetingMinutes(@PathVariable Long meetingId, @PathVariable Long fileId) {
        try {
            meetingService.deleteMeetingMinutes(meetingId, fileId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

}
