package com.codehows.daehobe.controller.issue;

import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.service.issue.IssueService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public List<IssueDto> getAllIssue(){return issueService.findAllNoDel();}
}
