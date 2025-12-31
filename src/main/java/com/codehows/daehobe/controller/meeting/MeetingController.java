package com.codehows.daehobe.controller.meeting;

import com.codehows.daehobe.dto.issue.FilterDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.service.meeting.MeetingService;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final MemberService memberService;

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

    //회의 목록 조회(페이징) + 검색/필터링
    @GetMapping("/list")
    public ResponseEntity<?> getMeetings(
            @ModelAttribute FilterDto filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<MeetingListDto> dtoList = meetingService.findAll(filter,pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회의 조회 중 오류 발생");
        }
    }

    //회의 캘린더 조회
    @GetMapping("/scheduler")
    public ResponseEntity<?> getMeetingByMonth(
            @RequestParam int year,
            @RequestParam int month
    ) {

        try {
            List<MeetingListDto> meetings =
                    meetingService.findByDateBetween(year, month);

            return ResponseEntity.ok(meetings);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회의 조회 중 오류 발생");
        }
    }

    //나의 업무 캘린더
    @GetMapping("/scheduler/mytask/{id}")
    public ResponseEntity<?> getMeetingByMonthForMember(
            @PathVariable Long id,
            @RequestParam int year,
            @RequestParam int month
    ) {
        System.out.println(" =============================================================");
        System.out.println(" getMeetingByMonthForMember id: " + id);
        System.out.println(" =============================================================");

        try {
            List<MeetingListDto> meetings =
                    meetingService.findByDateBetweenForMember(id, year, month);
            System.out.println(" meetings: " + meetings);
            return ResponseEntity.ok(meetings);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회의 조회 중 오류 발생");
        }
    }


    //나의 업무 회의 목록 조회(페이징) + 검색/필터링
    @GetMapping("/mytask/{id}")
    public ResponseEntity<?> getMeetingsById(
            @PathVariable Long id,
            @ModelAttribute FilterDto filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<MeetingListDto> memberMeetings = meetingService.getMeetingsForMember(id, filter, pageable);
            return ResponseEntity.ok(memberMeetings);
        } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("회의 조회 중 오류 발생");
        }
    }

    @GetMapping("/{id}/title")
    public ResponseEntity<String> getMeetingTitle(@PathVariable Long id) {
        Meeting meeting = meetingService.getMeetingById(id);
        return ResponseEntity.ok(meeting.getTitle());
    }

}
