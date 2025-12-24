package com.codehows.daehobe.controller.meeting;

import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.service.meeting.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            @RequestPart("data") MeetingFormDto meetingFormDto,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles,Authentication authentication) {

        Meeting meeting = meetingService.createMeeting(meetingFormDto, multipartFiles,authentication.getName());
        return ResponseEntity.ok(meeting.getId());

    }

    // 회의 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getMeetingDtl(@PathVariable Long id, Authentication authentication) {
        try {
            Long memberId = Long.valueOf(authentication.getName());
            MeetingDto meetingDto = meetingService.getMeetingDtl(id, memberId);
            return ResponseEntity.ok(meetingDto);
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

    // 회의 수정
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMeeting(
            @PathVariable Long id,
            @RequestPart("data") MeetingFormDto meetingFormDto,
            @RequestPart(value = "file", required = false) List<MultipartFile> filesToUpload,
            @RequestPart(value = "removeFileIds", required = false) List<Long> filesToRemove,
            Authentication authentication
    ) {
        try {
            List<MultipartFile> newFiles = filesToUpload != null ? filesToUpload : List.of();
            List<Long> removeFileIds = filesToRemove != null ? filesToRemove : List.of();
            meetingService.updateMeeting(id, meetingFormDto, newFiles, removeFileIds,authentication.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회의 수정 중 오류 발생");
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

    @GetMapping("/list")
    public ResponseEntity<?> getMeetings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<MeetingListDto> dtoList = meetingService.findAll(pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }
}
