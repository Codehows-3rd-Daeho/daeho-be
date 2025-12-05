package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.issue.IssueDtlDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.repository.issue.IssueDepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.masterData.DepartmentService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueMemberRepository issueMemberRepository;
    private final FileService fileService;
    private final IssueDepartmentService IssueDepartmentService;
    private final IssueMemberService issueMemberService;
    private final CategoryService categoryService;
    private final FileRepository fileRepository;
    private final IssueDepartmentRepository issueDepartmentRepository;
    private final MemberRepository memberRepository;

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
                .isDel(issueDto.getIsDel())
                .build();

        //이슈 먼저 저장 후 id를 파일 엔티티에 저장 가능
        issueRepository.save(saveIssue);

        System.out.println("이슈 id:  " + saveIssue.getIssueId() + "================================");


        //부서
        // 1. DTO에서 부서 이름 목록 (List<Long>) 추출
        List<Long> departmentIds = issueDto.getDepartmentIds();
        //2. 부서 저장 서비스 호출
        if(departmentIds != null && !departmentIds.isEmpty()) {
            IssueDepartmentService.saveDepartment(saveIssue.getIssueId(), departmentIds);
            System.out.println("부서 서비스 작동 확인==================================");
        }


        //참여자
        // 1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<IssueMemberDto> issueMemberDtos = issueDto.getMembers();
        //2. 참여자 저장 서비스 호출
        System.out.println("issueMemberDtos ================================"+ issueMemberDtos);
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(saveIssue.getIssueId(), issueMemberDtos);
            System.out.println("참여자 서비스 작동 확인================================");
        }


        //파일저장 서비스 호출
        if(multipartFiles != null) {
            fileService.uploadFiles(saveIssue.getIssueId(), multipartFiles, TargetType.ISSUE);
            System.out.println("파일 서비스 작동 확인================================");
        }

        return saveIssue;
    }

    public IssueDtlDto getIssueDtl(Long id) {
        // 이슈
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        // 주관자
        IssueMember host = issueMemberRepository.findByIssueIdAndIsHost(issue, true);
        String hostWithPos = host.getMemberId().getName() + host.getMemberId().getJobPosition().getName();
        // 이슈 파일
        List<File> files = fileRepository.findByTargetIdAndTargetType(id,TargetType.ISSUE);
        // 카테고리
        Category category = categoryService.getCategoryById(issue.getCategoryId().getId());
        // 부서들
        List<IssueDepartment> dptList = issueDepartmentRepository.findByIssueId(issue);
        List<String> departmentNames = new ArrayList<>();
        for (IssueDepartment dpt : dptList) {
            departmentNames.add(dpt.getDepartmentId().getName());
        }

        IssueDtlDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .status(String.valueOf(issue.getStatus()))
                .host(hostWithPos)
                .startDate(String.valueOf(issue.getStartDate()))
                .endDate(String.valueOf(issue.getEndDate()))
                .categoryName(category.getName())
                .departmentName(departmentNames)
                .createdAt(String.valueOf(issue.getCreatedAt()))
                .updatedAt(String.valueOf(issue.getUpdatedAt()))
                .build();

        return new IssueDtlDto();
    }
}
