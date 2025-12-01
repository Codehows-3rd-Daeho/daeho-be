package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service

@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final FileService fileService;
    private final IssueDepartmentService IssueDepartmentService;
    private final IssueMemberService issueMemberService;
    final CategoryService categoryService;

    public Issue createIssue(IssueDto issueDto , List<MultipartFile> multipartFiles) {

        // 1. DTO에서 categoryId를 가져와 실제 엔티티 조회
        Category categoryId = categoryService.getCategoryById(issueDto.getCategoryId());

        //entity에 dto로 받은 값 넣기(builder 사용)
        Issue saveIssue = Issue.builder()
                .title(issueDto.getTitle())
                .content(issueDto.getContent())
                .status(Status.valueOf(issueDto.getStatus()))
                .categoryId(categoryId)
                .startDate(issueDto.getStartDate())
                .endDate(issueDto.getEndDate())
//                .memberId((issueDto.getMembersId())
                .isDel(false)
                .build();

        //이슈 먼저 저장 후 id를 파일 엔티티에 저장 가능
        issueRepository.save(saveIssue);

        System.out.println("이슈 id:  " + saveIssue.getIssueId());


        //부서
        // 1. DTO에서 부서 이름 목록 (List<String>) 추출
        List<Long> departmentId = issueDto.getDepartmentIds();
        //2. 부서 저장 서비스 호출
        if(departmentId != null && !departmentId.isEmpty()) {
            IssueDepartmentService.saveDepartment(saveIssue.getIssueId(), departmentId);
        }

        //참여자
        // 1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<Long> memberId = issueDto.getMemberIds();
        //2. 참여자 저장 서비스 호출
        if(memberId != null && !memberId.isEmpty()) {
            issueMemberService.saveIssueMember(saveIssue.getIssueId(), memberId);
        }


        //파일저장 서비스 호출
        if(multipartFiles != null) {
            fileService.uploadFiles(saveIssue.getIssueId(),  multipartFiles);
        }

        return saveIssue;

    }
}
