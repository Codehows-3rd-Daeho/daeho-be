package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Category;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
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

    public Issue createIssue(IssueFormDto issueFormDto, List<MultipartFile> multipartFiles) {

        // 1. DTO에서 categoryId를 가져와 실제 엔티티 조회
        Category categoryId = categoryService.getCategoryById(issueFormDto.getCategoryId());

        //entity에 dto로 받은 값 넣기(builder 사용)
        Issue saveIssue = Issue.builder()
                .title(issueFormDto.getTitle())
                .content(issueFormDto.getContent())
                .status(Status.valueOf(issueFormDto.getStatus()))
                .category(categoryId)
                .startDate(issueFormDto.getStartDate())
                .endDate(issueFormDto.getEndDate())
                .isDel(issueFormDto.getIsDel())
                .build();

        //이슈 먼저 저장 후 id를 파일 엔티티에 저장 가능
        issueRepository.save(saveIssue);


        //부서
        // 1. DTO에서 부서 이름 목록 (List<Long>) 추출
        List<Long> departmentIds = issueFormDto.getDepartmentIds();
        //2. 부서 저장 서비스 호출
        if (departmentIds != null && !departmentIds.isEmpty()) {
            IssueDepartmentService.saveDepartment(saveIssue.getId(), departmentIds);
        }


        //참여자
        // 1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<IssueMemberDto> issueMemberDtos = issueFormDto.getMembers();
        //2. 참여자 저장 서비스 호출
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            issueMemberService.saveIssueMember(saveIssue.getId(), issueMemberDtos);
        }


        //파일저장 서비스 호출
        if (multipartFiles != null) {
            fileService.uploadFiles(saveIssue.getId(), multipartFiles, TargetType.ISSUE);
        }

        return saveIssue;
    }

    public IssueDto getIssueDtl(Long id, Long memberId) {
        // 이슈
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        // 주관자
        IssueMember host = issueMemberRepository.findByIssueAndIsHost(issue, true);
        String hostName = (host != null) ? host.getMember().getName() : null;
        String hostJPName = (host != null) ? host.getMember().getJobPosition().getName() : null;
        // 이슈 파일
        List<FileDto> fileDtoList = fileRepository.findByTargetIdAndTargetType(id, TargetType.ISSUE)
                .stream()
                .map(FileDto::fromEntity)
                .toList();
        // 카테고리
        String category = issue.getCategory().getName();
        // 부서들
        List<String> departmentNames = issueDepartmentRepository.findByIssue(issue)
                .stream()
                .map(dpt -> dpt.getDepartment().getName())
                .toList();

        // 유저가 해당 게시글의 수정,삭제 권한을 갖고있는지.
        Member member = memberRepository.findById(memberId).orElseThrow(EntityNotFoundException::new);
        IssueMember issueMember = issueMemberRepository.findByIssueAndMember(issue, member).orElse(null);
        boolean isEditPermitted = issueMember != null && issueMember.isPermitted(); //이 사용자에게 수정 권한이 있을 때만 true

        // 참여자
        List<IssueMemberDto> participantList = issueMemberRepository.findByIssue(issue)
                .stream()
                .map(IssueMemberDto::fromEntity)
                .toList();


        return IssueDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .fileList(fileDtoList)
                .status(issue.getStatus().toString())
                .hostName(hostName)
                .hostJPName(hostJPName)
                .startDate(String.valueOf(issue.getStartDate()))
                .endDate(String.valueOf(issue.getEndDate()))
                .categoryName(category)
                .departmentName(departmentNames)
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .del(issue.isDel())
                .editPermitted(isEditPermitted)
                .participantList(participantList)
                .build();

    }

    //이슈 조회
    public List<IssueFormDto> getIssueInMeeting() {
        List<Issue> issues = issueRepository.findAllByIsDelFalse();

        return issues.stream()
                .map(issue -> convertToDto(issue)) // 변환 메서드 호출
                .toList();
    }

    //선택된 이슈 조회
    public IssueFormDto getSelectedINM(Long id) {
        Issue issue = issueRepository.findByIdAndIsDelFalse(id)
                .orElseThrow(() -> new RuntimeException("삭제되지 않은 이슈가 존재하지 않습니다."));


        return convertToDto(issue);
    }

    private IssueFormDto convertToDto(Issue issue) {

        // host 찾기
        IssueMember host = issueMemberRepository.findByIssueAndIsHost(issue, true);

        // 부서 찾기
        List<IssueDepartment> departmentIds = issueDepartmentRepository.findByIssue(issue);

        // 멤버 목록
        List<IssueMember> members = issueMemberRepository.findAllByIssue(issue);

        return IssueFormDto.builder()
                .id(issue.getId())//회의에서 이슈 get할때 필요
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
                .categoryId(issue.getCategory().getId())
//                .startDate(issue.getStartDate())
//                .endDate(issue.getEndDate())
                //부서
                .departmentIds(
                        departmentIds.stream()
                                .map(d -> d.getDepartment().getId())
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
        IssueMember issueMember = issueMemberRepository.findByIssueAndMember(issue, member).orElseThrow(EntityNotFoundException::new);
        if (issueMember.isRead()) {
            return;
        }
        issueMember.updateIsRead(true);
    }


    public Issue updateIssue(Long id, IssueFormDto issueFormDto, List<MultipartFile> newFiles, List<Long> removeFileIds) {
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        Category category = categoryService.getCategoryById(issueFormDto.getCategoryId());
        issue.update(issueFormDto, category);

        // 이슈 부서 엔티티 삭제 후 추가
        issueDepartmentRepository.deleteByIssue(issue);
        List<Long> departmentIds = issueFormDto.getDepartmentIds();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            IssueDepartmentService.saveDepartment(id, departmentIds);
        }

        // 이슈 참여자 엔티티 삭제 후 추가
        issueMemberRepository.deleteByIssue(issue);
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

    public void deleteIssue(Long id) {
        Issue issue = issueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("이슈가 존재하지 않습니다."));
        issue.delete();
    }

//    // 이슈 전체 조회(삭제X)
//    public Page<IssueListDto> findAll(Pageable pageable) {
//        return issueRepository.findAllWithStatusSort(pageable)
//            .map(IssueListDto::fromEntity);
//    }
//
//    // 칸반 조회
//    // 진행중
//    public List<IssueListDto> getInProgress() {
//        return issueRepository.findInProgress()
//                .stream()
//                .map(IssueListDto::fromEntity)
//                .toList();
//    }
//
//    // 미결
//    public List<IssueListDto> getDelayed() {
//        return issueRepository.findDelayed()
//                .stream()
//                .map(IssueListDto::fromEntity)
//                .toList();
//    }
//
//    // 완료(최근 7일)
//    public List<IssueListDto> getCompleted() {
//        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
//
//        return issueRepository.findRecentCompleted(sevenDaysAgo)
//                .stream()
//                .map(IssueListDto::fromEntity)
//                .toList();
//    }
//

}
