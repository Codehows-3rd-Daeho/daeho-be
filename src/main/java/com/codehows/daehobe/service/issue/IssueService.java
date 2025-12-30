package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.aop.TrackChanges;
import com.codehows.daehobe.aop.TrackMemberChanges;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.dto.masterData.SetNotificationDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.masterData.SetNotificationService;
import com.codehows.daehobe.service.member.MemberService;
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
    private final MemberService memberService;
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
                    "/issue/" + saveIssue.getId()
            );
        }

        return saveIssue;
    }

    public IssueDto getIssueDtl(Long id, Long memberId) {

        Issue issue = issueRepository.findDetailById(id)
                .orElseThrow(() -> new RuntimeException("이슈가 존재하지 않습니다."));

        List<IssueMember> issueMembers = issue.getIssueMembers();

        IssueMember host = issueMembers.stream()
                .filter(IssueMember::isHost)
                .findFirst()
                .orElse(null);

        boolean isEditPermitted = issueMembers.stream()
                .anyMatch(im ->
                        im.getMember().getId().equals(memberId) && im.isPermitted()
                );

        List<IssueMemberDto> participantList = issueMembers.stream()
                .map(IssueMemberDto::fromEntity)
                .toList();

        List<FileDto> fileDtoList = fileService.getIssueFiles(id);
        List<String> departmentNames =
                issueDepartmentService.getDepartmentName(issue);

        return IssueDto.fromEntity(
                issue,
                host,
                departmentNames,
                fileDtoList,
                isEditPermitted,
                participantList
        );
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
                    "/issue/" + issue.getId()
            );
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

    // issueId로 이슈 조회
    public Issue getIssueById(Long issueId) {
        return issueRepository.findById(issueId).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
    }

//    ================================================나의 업무=================================================================

    //memberId가 참여한 이슈만 추출(공통 로직)
    public List<IssueListDto> getIssuesForMember(Long memberId, List<Issue> issues) {
        return issues.stream()
                .filter(issue -> issueMemberService.isParticipant(memberId, issue)) // 참여자만 필터
                .map(this::toIssueListDto) // DTO 변환
                .toList();
    }


    //진행 중인 이슈 중에서 해당 멤버가 참여한 것만 추출
    public List<IssueListDto> getInProgressForMember(Long memberId) {
        return getIssuesForMember(memberId, issueRepository.findInProgress());
    }

    //지연된 이슈 중에서 해당 멤버가 참여한 것만 추출
    public List<IssueListDto> getDelayedForMember(Long memberId) {
        return getIssuesForMember(memberId, issueRepository.findDelayed());
    }

    //완료된 이슈 중에서 해당 멤버가 참여한 것만 추출
    public List<IssueListDto> getCompletedForMember(Long memberId) {
        LocalDate setDate = LocalDate.now().minusDays(7);
        return getIssuesForMember(memberId, issueRepository.findRecentCompleted(setDate));
    }

    //이슈 리스트
    //퀴리로 참여자 필터 후 페이징
    public List<IssueListDto> getIssuesForMember(Long memberId, Pageable pageable) {

        Page<IssueMember> issueMembers = issueMemberService.findByMemberId(memberId, pageable);

        return issueMembers.getContent().stream()//stream으로 Page안의 객체를 매핑
                .map(IssueMember::getIssue)
                .map(this::toIssueListDto)
                .toList();
    }

}
