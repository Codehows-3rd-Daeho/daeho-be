package com.codehows.daehobe.controller.issue;

import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.service.issue.IssueService;
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
@RequestMapping("/issue")
@RequiredArgsConstructor
public class IssueController {
    private final IssueService issueService;
    private final MeetingService meetingService;

    @PostMapping("/create")
    public ResponseEntity<?> createIssue(
            @RequestPart("data") IssueFormDto issueFormDto,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles) {

        Issue issue = issueService.createIssue(issueFormDto, multipartFiles);
        return ResponseEntity.ok(issue.getId());

    }


    //칸반 전체
    @GetMapping("/kanban")
    public ResponseEntity<?> getKanbanData() {

        var inProgress = issueService.getInProgress();       // 진행중 전체
        var delayed = issueService.getDelayed();             // 미결 전체
        var completed = issueService.getCompleted();         // 최근 7일 완료 전체

        return ResponseEntity.ok(
                new KanbanResponse(inProgress, delayed, completed)
        );
    }



    // 내부 응답 DTO
    record KanbanResponse(
            List<IssueListDto> inProgress,
            List<IssueListDto> delayed,
            List<IssueListDto> completed
    ) {
    }

    // 리스트 전체조회()
    @GetMapping("/list")
    public ResponseEntity<?> getIssues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<IssueListDto> dtoList = issueService.findAll(pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getIssueDtl(@PathVariable Long id, Authentication authentication) {
        // 요청한 회원의 id
        try {
            Long memberId = Long.valueOf(authentication.getName());
            IssueDto issueDto = issueService.getIssueDtl(id, memberId);
            return ResponseEntity.ok(issueDto);
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
            issueService.updateReadStatus(id, memberId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 이슈 수정
    @PutMapping("/{id}")
    public ResponseEntity<?> updateIssue(
            @PathVariable Long id,
            @RequestPart("data") IssueFormDto issueFormDto,
            @RequestPart(value = "file", required = false) List<MultipartFile> filesToUpload,
            @RequestPart(value = "removeFileIds", required = false) List<Long> filesToRemove
    ) {
        try {
            List<MultipartFile> newFiles = filesToUpload != null ? filesToUpload : List.of();
            List<Long> removeFileIds = filesToRemove != null ? filesToRemove : List.of();
            issueService.updateIssue(id, issueFormDto, newFiles, removeFileIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 수정 중 오류 발생");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteIssue(@PathVariable Long id) {
        try {
            issueService.deleteIssue(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 삭제 중 오류 발생");
        }
    }

    @GetMapping("/related")
    public ResponseEntity<?> getIssueInMeeting() {
        List<IssueFormDto> issueFormDto = issueService.getIssueInMeeting();
        return ResponseEntity.ok(issueFormDto);
    }

    @GetMapping("/related/{id}")
    public ResponseEntity<?> getSelectedINM(@PathVariable Long id) {
        IssueFormDto issueFormDto = issueService.getSelectedINM(id);
        return ResponseEntity.ok(issueFormDto);
    }

    @GetMapping("/{id}/meeting")
    public ResponseEntity<?> getMeetingRelatedIssue(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<MeetingListDto> meetingListDtos = meetingService.getMeetingRelatedIssue(id,pageable);
            return ResponseEntity.ok(meetingListDtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

//    ================================================나의 업무=================================================================

    //    나의 업무 칸반
    @GetMapping("/kanban/mytask/{id}")
    public ResponseEntity<?> getKanbanDataById(@PathVariable Long id
    ) {
        System.out.println(" =============================================================");

        System.out.println(" getKanbanDataById id: " + id);
        var inProgress = issueService.getInProgressForMember(id);       // 진행중 전체
        var delayed = issueService.getDelayedForMember(id);             // 미결 전체
        var completed = issueService.getCompletedForMember(id);         // 최근 7일 완료 전체

        return ResponseEntity.ok(
                new KanbanResponse(inProgress, delayed, completed)
        );
    }

    //나의 업무 리스트
    @GetMapping("/list/mytask/{id}")
    public ResponseEntity<?> getIssuesById(@PathVariable Long id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            List<IssueListDto> memberIssues = issueService.getIssuesForMember(id, pageable);
            return ResponseEntity.ok(memberIssues);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }

}
