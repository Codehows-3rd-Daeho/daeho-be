package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.aop.TrackChanges;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.meeting.MeetingService;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final FileService fileService;
    private final IssueDepartmentService issueDepartmentService;
    private final IssueMemberService issueMemberService;
    private final CategoryService categoryService;
    private final MemberService memberService;

    @TrackChanges(type = ChangeType.CREATE, target = TargetType.ISSUE)
    public Issue createIssue(IssueFormDto issueFormDto, List<MultipartFile> multipartFiles) {

        // 1. DTO에서 categoryId를 가져와 실제 엔티티 조회
        Category categoryId = categoryService.getCategoryById(issueFormDto.getCategoryId());

        // entity에 dto로 받은 값 넣기(builder 사용)
        Issue saveIssue = Issue.builder()
                .title(issueFormDto.getTitle())
                .content(issueFormDto.getContent())
                .status(Status.valueOf(issueFormDto.getStatus()))
                .category(categoryId)
                .startDate(issueFormDto.getStartDate())
                .endDate(issueFormDto.getEndDate())
                .isDel(issueFormDto.getIsDel())
                .build();

        // 이슈 먼저 저장 후 id를 파일 엔티티에 저장 가능
        issueRepository.save(saveIssue);

        // 부서
        // 1. DTO에서 부서 이름 목록 (List<Long>) 추출
        List<Long> departmentIds = issueFormDto.getDepartmentIds();
        // 2. 부서 저장 서비스 호출
        if (departmentIds != null && !departmentIds.isEmpty()) {
            issueDepartmentService.saveDepartment(saveIssue.getId(), departmentIds);
        }

        // 참여자
        // 1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<IssueMemberDto> issueMemberDtos = issueFormDto.getMembers();
        // 2. 참여자 저장 서비스 호출
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(saveIssue.getId(), issueMemberDtos);
        }

        // 파일저장 서비스 호출
        if (multipartFiles != null) {
            fileService.uploadFiles(saveIssue.getId(), multipartFiles, TargetType.ISSUE);
        }

        return saveIssue;
    }

    public IssueDto getIssueDtl(Long id, Long memberId) {
        // 이슈
        Issue issue = getIssueById(id);
        // 해당 이슈의 모든 참여자
        List<IssueMember> issueMembers = issueMemberService.getMembers(issue);
        // 주관자
        IssueMember host = issueMembers.stream()
                .filter(IssueMember::isHost)
                .findFirst()
                .orElse(null);

        // 이슈 파일
        List<FileDto> fileDtoList = fileService.getIssueFiles(id);
        // 부서들
        List<String> departmentNames = issueDepartmentService.getDepartmentName(issue);

        // 유저가 해당 게시글의 수정,삭제 권한을 갖고있는지.
        boolean isEditPermitted = issueMembers.stream()
                .filter(im -> im.getMember().getId().equals(memberId))
                .anyMatch(IssueMember::isPermitted);

        // 참여자
        List<IssueMemberDto> participantList = issueMembers.stream()
                .map(IssueMemberDto::fromEntity)
                .toList();

        return IssueDto.fromEntity(
                issue,
                host,
                departmentNames,
                fileDtoList,
                isEditPermitted,
                participantList);

    }

    // 이슈 조회
    public List<IssueFormDto> getIssueInMeeting() {
        List<Issue> issues = issueRepository.findAllByIsDelFalse();

        return issues.stream()
                .map(issue -> convertToDto(issue)) // 변환 메서드 호출
                .toList();
    }

    // 선택된 이슈 조회
    public IssueFormDto getSelectedINM(Long id) {
        Issue issue = issueRepository.findByIdAndIsDelFalseAndStatus(id, Status.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("삭제되지 않은 이슈가 존재하지 않습니다."));

        return convertToDto(issue);
    }

    private IssueFormDto convertToDto(Issue issue) {
        // 부서 찾기
        List<IssueDepartment> departmentIds = issueDepartmentService.getDepartMent(issue);

        // 멤버 목록
        List<IssueMember> members = issueMemberService.getMembers(issue);
        return IssueFormDto.fromEntity(issue, departmentIds, members);
    }

    public void updateReadStatus(Long id, Long memberId) {
        Member member = memberService.getMemberById(memberId);
        Issue issue = getIssueById(id);
        IssueMember issueMember = issueMemberService.getMember(issue, member);
        if (issueMember.isRead()) {
            return;
        }
        issueMember.updateIsRead(true);
    }

    @TrackChanges(type = ChangeType.UPDATE, target = TargetType.ISSUE)
    public Issue updateIssue(Long id, IssueFormDto issueFormDto, List<MultipartFile> newFiles,
                             List<Long> removeFileIds) {
        Issue issue = getIssueById(id);
        Category category = categoryService.getCategoryById(issueFormDto.getCategoryId());
        issue.update(issueFormDto, category);

        // 이슈 부서 엔티티 삭제 후 추가
        issueDepartmentService.deleteIssueDepartment(issue);
        List<Long> departmentIds = issueFormDto.getDepartmentIds();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            issueDepartmentService.saveDepartment(id, departmentIds);
        }

        // 이슈 참여자 엔티티 삭제 후 추가
        issueMemberService.deleteIssueMember(issue);
        List<IssueMemberDto> issueMemberDtos = issueFormDto.getMembers();
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(id, issueMemberDtos);
        }

        // 파일 업데이트
        if ((newFiles != null && !newFiles.isEmpty()) || (removeFileIds != null && !removeFileIds.isEmpty())) {
            fileService.updateFiles(id, newFiles, removeFileIds, TargetType.ISSUE);
        }
        return issue;
    }

    @TrackChanges(type = ChangeType.DELETE, target = TargetType.ISSUE)
    public Issue deleteIssue(Long id) {
        Issue issue = getIssueById(id);
        issue.delete();
        return issue;
    }

    // 이슈 전체 조회(삭제X)
    public Page<IssueListDto> findAll(Pageable pageable) {
        return issueRepository.findByIsDelFalse(pageable)
                .map(this::toIssueListDto);
    }

    // 칸반 조회 - 진행중
    public List<IssueListDto> getInProgress() {
        return issueRepository.findInProgress()
                .stream()
                .map(this::toIssueListDto)
                .toList();
    }

    // 미결
    public List<IssueListDto> getDelayed() {
        return issueRepository.findDelayed()
                .stream()
                .map(this::toIssueListDto)
                .toList();
    }

    // 완료(최근 7일)
    public List<IssueListDto> getCompleted() {
        LocalDate setDate = LocalDate.now().minusDays(7);
        return issueRepository.findRecentCompleted(setDate)
                .stream()
                .map(this::toIssueListDto)
                .toList();
    }

    // 리스트 공통부분
    private IssueListDto toIssueListDto(Issue issue) {
        IssueMember host = issueMemberService.getHost(issue);
        String hostName = (host != null) ? host.getMember().getName() : null; // 주관자 이름
        String hostJPName = (host != null && host.getMember().getJobPosition() != null) // 주관자 직급
                ? host.getMember().getJobPosition().getName()
                : null;
        List<String> departmentName = issueDepartmentService.getDepartmentName(issue);
        return IssueListDto.fromEntity(issue, departmentName, hostName, hostJPName);
    }

    // issueId로 이슈 조회
    public Issue getIssueById(Long issueId) {
        return issueRepository.findById(issueId).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
    }
}
