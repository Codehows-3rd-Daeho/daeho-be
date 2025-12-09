package com.codehows.daehobe.controller.issue;

import com.codehows.daehobe.dto.issue.IssueDtlDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.service.issue.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/issue")
@RequiredArgsConstructor
public class IssueController {
    private final IssueService issueService;

    //ResponseEntity: HTTP 상태 코드, 헤더 등을 함께 설정 가능하게 하는 wrapper(다른 객체나 값을 감싸는 객체)
    //<T>: 보낼 데이터
    @PostMapping("/create")
    public ResponseEntity<?> createIssue(
            @RequestPart("data") IssueDto issueDto ,
            @RequestPart(value = "file", required = false)List<MultipartFile> multipartFiles){

        System.out.println("==============이슈 등록 시작");
        issueService.createIssue(issueDto, multipartFiles);

        return ResponseEntity.ok().build();

    }

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
    ){}

    // 리스트 전체조회()
    @GetMapping("/list")
    public ResponseEntity<?> getIssues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<IssueListDto> dtoList = issueService.findAll(pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getIssueDtl(@PathVariable Long id){
        IssueDtlDto issueDtlDto = issueService.getIssueDtl(id);
        return ResponseEntity.ok(issueDtlDto);
    }
}
