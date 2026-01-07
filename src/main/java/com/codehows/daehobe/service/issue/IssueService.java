package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.aop.TrackChanges;
import com.codehows.daehobe.aop.TrackMemberChanges;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.*;
import com.codehows.daehobe.dto.masterData.SetNotificationDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.masterData.SetNotificationService;
import com.codehows.daehobe.service.webpush.NotificationService;
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
    private final NotificationService notificationService;
    private final SetNotificationService setNotificationService;

    @TrackChanges(type = ChangeType.CREATE, target = TargetType.ISSUE)
    public Issue createIssue(IssueFormDto issueFormDto, List<MultipartFile> multipartFiles, String writerId) {

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
                .isPrivate(issueFormDto.getIsPrivate())
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

        // 알림 발송
        SetNotificationDto settingdto = setNotificationService.getSetting();// 알림 설정 가져오기
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty() && settingdto.isIssueCreated()) {
            notificationService.notifyMembers(issueMemberDtos.stream()
                            .map(IssueMemberDto::getId)
                            .toList(),
                    Long.valueOf(writerId),
                    "새 이슈가 등록되었습니다 \n" + saveIssue.getTitle(),
                    "/issue/" + saveIssue.getId());
        }

        return saveIssue;
    }

    public IssueDto getIssueDtl(Long id, Long memberId) {

        Issue issue = issueRepository.findDetailById(id)
                .orElseThrow(() -> new RuntimeException("이슈가 존재하지 않습니다."));

        if (issue.isPrivate()) {
            boolean isParticipant = issue.getIssueMembers().stream()
                    .anyMatch(im -> im.getMember().getId().equals(memberId));
            if (!isParticipant) {
                throw new RuntimeException("권한이 없는 비밀글입니다.");
            }
        }

        List<IssueMember> issueMembers = issue.getIssueMembers();

        IssueMember host = issueMembers.stream()
                .filter(IssueMember::isHost)
                .findFirst()
                .orElse(null);

        boolean isEditPermitted = issueMembers.stream()
                .anyMatch(im -> im.getMember().getId().equals(memberId) && im.isPermitted());

        List<IssueMemberDto> participantList = issueMembers.stream()
                .map(IssueMemberDto::fromEntity)
                .toList();

        List<FileDto> fileDtoList = fileService.getIssueFiles(id);
        List<String> departmentNames = issueDepartmentService.getDepartmentName(issue);

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
        List<Issue> issues = issueRepository.findAllByIsDelFalseAndStatus(Status.IN_PROGRESS);

        return issues.stream()
                .map(issue -> convertToDto(issue)) // 변환 메서드 호출
                .toList();
    }

    // 선택된 이슈 조회
    public IssueFormDto getSelectedINM(Long id) {
        Issue issue = issueRepository.findByIdAndIsDelFalseAndStatus(id, Status.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("삭제되지 않았거나 진행중인 이슈가 아닙니다."));

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
        IssueMember issueMember = issueMemberService.findByIssueIdAndMemberId(id, memberId);
        if (issueMember.isRead()) {
            return;
        }
        issueMember.updateIsRead(true);
    }

    @TrackChanges(type = ChangeType.UPDATE, target = TargetType.ISSUE)
    @TrackMemberChanges(target = TargetType.ISSUE)
    public Issue updateIssue(Long id, IssueFormDto issueFormDto, List<MultipartFile> newFiles,
                             List<Long> removeFileIds, String writerId) {
        Issue issue = getIssueById(id);
        Category category = categoryService.getCategoryById(issueFormDto.getCategoryId());

        Status beforeStatus = issue.getStatus(); // 수정전 상태
        issue.update(issueFormDto, category);
        Status afterStatus = issue.getStatus(); // 수정후 상태

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

        // 상태 변경 알림
        SetNotificationDto settingdto = setNotificationService.getSetting();// 알림 설정 가져오기
        if (!beforeStatus.equals(afterStatus) && issueMemberDtos != null && settingdto.isIssueStatus()) {
            notificationService.notifyMembers(issueMemberDtos.stream()
                            .map(IssueMemberDto::getId)
                            .toList(),
                    Long.valueOf(writerId),
                    "이슈 상태가 변경되었습니다 \n" +
                            beforeStatus.getLabel() + " → " + afterStatus.getLabel(),
                    "/issue/" + issue.getId());
        }

        return issue;
    }

    @TrackChanges(type = ChangeType.DELETE, target = TargetType.ISSUE)
    public Issue deleteIssue(Long id) {
        Issue issue = getIssueById(id);
        issue.delete();
        return issue;
    }


    // 1. 칸반: 진행중
    public List<IssueListDto> getInProgress(FilterDto filter, Long memberId) {
        return getFilteredIssueList(filter, Status.IN_PROGRESS, false, null, memberId);
    }

    // 2. 칸반: 미결
    public List<IssueListDto> getDelayed(FilterDto filter, Long memberId) {
        return getFilteredIssueList(filter, Status.IN_PROGRESS, true, null, memberId);
    }

    // 3. 칸반: 완료 (최근 7일)
    public List<IssueListDto> getCompleted(FilterDto filter, Long memberId) {
        return getFilteredIssueList(filter, Status.COMPLETED, false, LocalDate.now().minusDays(7), memberId);
    }

    // 4. 일반 리스트 조회 (전체)
    public Page<IssueListDto> findAll(FilterDto filter, Pageable pageable, Long memberId) {
        return issueRepository.findIssuesWithFilter(filter, null, false, null, memberId, pageable)
                .map(this::toIssueListDto);
    }

    // ========================== 나의 업무 ==========================

    // 1. 나의 업무 칸반: 진행중
    public List<IssueListDto> getInProgressForMember(Long memberId, FilterDto filter) {
        // memberId를 파라미터로 전달하여 해당 사용자가 참여한 이슈만 필터링
        return getFilteredIssueList(filter, Status.IN_PROGRESS, false, null, memberId);
    }

    // 2. 나의 업무 칸반: 미결 (기한 만료)
    public List<IssueListDto> getDelayedForMember(Long memberId, FilterDto filter) {
        // isDelayed = true 로 전달하여 기한 지난 건만 필터링
        return getFilteredIssueList(filter, Status.IN_PROGRESS, true, null, memberId);
    }

    // 3. 나의 업무 칸반: 완료 (최근 7일)
    public List<IssueListDto> getCompletedForMember(Long memberId, FilterDto filter) {
        LocalDate setDate = LocalDate.now().minusDays(7);
        return getFilteredIssueList(filter, Status.COMPLETED, false, setDate, memberId);
    }

    // 4. 나의 업무 리스트 (페이징)
    public List<IssueListDto> getIssuesForMember(Long memberId, FilterDto filter, Pageable pageable) {
        // Repository의 통합 쿼리를 직접 호출하여 페이징 결과를 가져옴
        return issueRepository.findIssuesWithFilter(filter, null, false, null, memberId, pageable)
                .map(this::toIssueListDto)
                .getContent();
    }

    // 리스트 공통부분(Entity -> Dto, 주관자 정보, 부서 정보 )
    private IssueListDto toIssueListDto(Issue issue) {
        IssueMember host = issueMemberService.getHost(issue);
        String hostName = (host != null) ? host.getMember().getName() : null; // 주관자 이름
        String hostJPName = (host != null && host.getMember().getJobPosition() != null) // 주관자 직급
                ? host.getMember().getJobPosition().getName()
                : null;
        List<String> departmentName = issueDepartmentService.getDepartmentName(issue);
        return IssueListDto.fromEntity(issue, departmentName, hostName, hostJPName);
    }

    // 공통 호출 메서드 (내부용)
    private List<IssueListDto> getFilteredIssueList(FilterDto filter, Status status, boolean isDelayed, LocalDate setDate, Long memberId) {
        return issueRepository.findIssuesWithFilter(filter, status, isDelayed, setDate, memberId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::toIssueListDto)
                .toList();
    }

    // issueId로 이슈 조회
    public Issue getIssueById(Long issueId) {
        return issueRepository.findById(issueId).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
    }
}
