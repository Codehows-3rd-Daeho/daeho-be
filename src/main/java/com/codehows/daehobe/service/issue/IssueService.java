package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.IssueDtlDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.repository.issue.IssueDepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public Issue createIssue(IssueDto issueDto, List<MultipartFile> multipartFiles) {

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
        if (departmentIds != null && !departmentIds.isEmpty()) {
            IssueDepartmentService.saveDepartment(saveIssue.getIssueId(), departmentIds);
            System.out.println("부서 서비스 작동 확인==================================");
        }


        //참여자
        // 1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<IssueMemberDto> issueMemberDtos = issueDto.getMembers();
        //2. 참여자 저장 서비스 호출
        System.out.println("issueMemberDtos ================================" + issueMemberDtos);
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(saveIssue.getIssueId(), issueMemberDtos);
            System.out.println("참여자 서비스 작동 확인================================");
        }


        //파일저장 서비스 호출
        if (multipartFiles != null) {
            fileService.uploadFiles(saveIssue.getIssueId(), multipartFiles, TargetType.ISSUE);
            System.out.println("파일 서비스 작동 확인================================");
        }

        return saveIssue;
    }

    public IssueDtlDto getIssueDtl(Long id, Long memberId) {
        // 이슈
        Issue issue = issueRepository.findByIssueId(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        // 주관자
        IssueMember host = issueMemberRepository.findByIssueIdAndIsHost(issue, true);
        String hostWithPos = (host != null)
                ? host.getMemberId().getName() + " " + host.getMemberId().getJobPosition().getName()
                : null;
        // 이슈 파일
        List<FileDto> fileDtoList = fileRepository.findByTargetIdAndTargetType(id, TargetType.ISSUE)
                .stream()
                .map(FileDto::fromEntity)
                .toList();
        // 카테고리
        String category = issue.getCategoryId().getName();
        // 부서들
        List<String> departmentNames = issueDepartmentRepository.findByIssueId(issue)
                .stream()
                .map(dpt -> dpt.getDepartmentId().getName())
                .toList();

        // 유저가 해당 게시글의 수정,삭제 권한을 갖고있는지.
        Member member = memberRepository.findById(memberId).orElseThrow(EntityNotFoundException::new);
        IssueMember issueMember = issueMemberRepository.findByIssueIdAndMemberId(issue, member).orElse(null);
        boolean isEditPermitted = issueMember != null && issueMember.isPermitted(); //이 사용자에게 수정 권한이 있을 때만 true

        // 참여자
        List<IssueMemberDto> participantList = issueMemberRepository.findByIssueId(issue)
                .stream()
                .map(IssueMemberDto::fromEntity)
                .toList();

        return IssueDtlDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .fileList(fileDtoList)
                .status(issue.getStatus().toString())
                .host(hostWithPos)
                .startDate(String.valueOf(issue.getStartDate()))
                .endDate(String.valueOf(issue.getEndDate()))
                .categoryName(category)
                .departmentName(departmentNames)
                .createdAt(issue.getCreatedAt().format(dateFormatter))
                .updatedAt(issue.getUpdatedAt().format(dateFormatter))
                .isDel(issue.isDel())
                .isEditPermitted(isEditPermitted)
                .participantList(participantList)
                .build();

    }

    //이슈 조회
    public List<IssueDto> getIssueInMeeting() {
        List<Issue> issues = issueRepository.findAllByIsDelFalse();

        return issues.stream()
                .map(issue -> convertToDto(issue)) // 변환 메서드 호출
                .toList();
    }

    //선택된 이슈 조회
    public IssueDto getSelectedINM(Long id) {
        Issue issue = issueRepository.findByIssueIdAndIsDelFalse(id)
                .orElseThrow(() -> new RuntimeException("삭제되지 않은 이슈가 존재하지 않습니다."));


        return convertToDto(issue);
    }

    private IssueDto convertToDto(Issue issue) {

        // host 찾기
        IssueMember host = issueMemberRepository.findByIssueIdAndIsHost(issue, true);

        // 부서 찾기
        List<IssueDepartment> departmentIds = issueDepartmentRepository.findByIssueId(issue);

        // 멤버 목록
        List<IssueMember> members = issueMemberRepository.findAllByIssueId(issue);

        return IssueDto.builder()
                .id(issue.getIssueId())//회의에서 이슈 get할때 필요
                .title(issue.getTitle())
//                .content(issue.getContent())
                .status(issue.getStatus().name())
//                //주관자
//                .host(
//                        Optional.ofNullable(host)
//                                .map(IssueMember::getMemberId)
//                                .map(Member::getName)
//                                .orElse(null)
//                )
                .categoryId(issue.getCategoryId().getId())
//                .startDate(issue.getStartDate())
//                .endDate(issue.getEndDate())
                //부서
                .departmentIds(
                        departmentIds.stream()
                                .map(d -> d.getDepartmentId().getId())
                                .toList()
                )
                //이슈 참여자
                .members(
                        members.stream()
                                .map(IssueMemberDto::fromEntity)
                                .toList()
                )
                .isDel(issue.isDel())
                .build();
    }

    public void updateReadStatus(Long id, Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(EntityNotFoundException::new);
        Issue issue = issueRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        IssueMember issueMember = issueMemberRepository.findByIssueIdAndMemberId(issue, member).orElseThrow(EntityNotFoundException::new);
        if (issueMember.isRead()) {
            return;
        }
        issueMember.updateIsRead(true);
    }


    public Issue updateIssue(Long id, IssueDto issueDto, List<MultipartFile> newFiles, List<Long> removeFileIds) {
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        Category category = categoryService.getCategoryById(issueDto.getCategoryId());
        issue.update(issueDto, category);

        // 이슈 부서 엔티티 삭제 후 추가
        issueDepartmentRepository.deleteByIssueId(issue);
        List<Long> departmentIds = issueDto.getDepartmentIds();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            IssueDepartmentService.saveDepartment(id, departmentIds);
        }

        // 이슈 참여자 엔티티 삭제 후 추가
        issueMemberRepository.deleteByIssueId(issue);
        List<IssueMemberDto> issueMemberDtos = issueDto.getMembers();
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(id, issueMemberDtos);
        }

        // 파일 업데이트
        if ((newFiles != null && !newFiles.isEmpty()) || (removeFileIds != null && !removeFileIds.isEmpty())) {
            fileService.updateFiles(id, newFiles, removeFileIds, TargetType.ISSUE);
        }
        return issue;
    }

    public void deleteIssue(Long id) {
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        issue.delete();
    }

    // 이슈 전체 조회(삭제X)
    public Page<IssueListDto> findAll(Pageable pageable) {
        return issueRepository.findAllWithStatusSort(pageable)
            .map(IssueListDto::fromEntity);
    }

    // 칸반 조회
    // 진행중
    public List<IssueListDto> getInProgress() {
        return issueRepository.findInProgress()
                .stream()
                .map(IssueListDto::fromEntity)
                .toList();
    }

    // 미결
    public List<IssueListDto> getDelayed() {
        return issueRepository.findDelayed()
                .stream()
                .map(IssueListDto::fromEntity)
                .toList();
    }

    // 완료(최근 7일)
    public List<IssueListDto> getCompleted() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);

        return issueRepository.findRecentCompleted(sevenDaysAgo)
                .stream()
                .map(IssueListDto::fromEntity)
                .toList();
    }


}
