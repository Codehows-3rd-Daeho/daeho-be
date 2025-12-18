package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.issue.IssueService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class MeetingService {

    private final CategoryService categoryService;
    private final MeetingRepository meetingRepository;
    private final FileService fileService;
    private final MeetingDepartmentService meetingDepartmentService;
    private final MeetingMemberService meetingMemberService;
    private final IssueService issueService;
    private final MemberService memberService;

    public Meeting createMeeting(MeetingFormDto meetingFormDto, List<MultipartFile> multipartFiles) {

        Category categoryId = categoryService.getCategoryById(meetingFormDto.getCategoryId());

        Long issueId = meetingFormDto.getIssueId();
        Issue issue = null;//이슈 없을 시 null값 사용
        if (issueId != 0) {
            issue = issueService.getIssueById(issueId);
        }

        //entity에 dto로 받은 값 넣기(builder 사용)
        Meeting saveMeeting = Meeting.builder()
                .title(meetingFormDto.getTitle())
                .content(meetingFormDto.getContent())
                .status(Status.valueOf(meetingFormDto.getStatus()))
                .issue(issue)
                .startDate(meetingFormDto.getStartDate())
                .endDate(meetingFormDto.getEndDate())
                .category(categoryId)
                .isDel(meetingFormDto.getIsDel())
                .build();

        meetingRepository.save(saveMeeting);

        //회의 부서
        // 1. DTO에서 부서 이름 목록 (List<Long>) 추출
        List<Long> departmentIds = meetingFormDto.getDepartmentIds();
        //2. 부서 저장 서비스 호출
        if (departmentIds != null && !departmentIds.isEmpty()) {
            meetingDepartmentService.saveDepartment(saveMeeting.getId(), departmentIds);
        }

        //회의 참여자
        //  1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<MeetingMemberDto> issueMemberDtos = meetingFormDto.getMembers();
        //2. 참여자 저장 서비스 호출
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            meetingMemberService.saveMeetingMember(saveMeeting.getId(), issueMemberDtos);

        }

        //파일 저장
        if (multipartFiles != null) {
            fileService.uploadFiles(saveMeeting.getId(), multipartFiles, TargetType.MEETING);
        }

        return saveMeeting;
    }

    public MeetingDto getMeetingDtl(Long id, Long memberId) {
        // 회의
        Meeting meeting = getMeetingById(id);
        // 해당 회의의 모든 참여자
        List<MeetingMember> meetingMembers = meetingMemberService.getMembers(meeting);
        // 주관자
        MeetingMember host = meetingMembers.stream()
                .filter(MeetingMember::isHost)
                .findFirst()
                .orElse(null);

        // 회의록
        File minutesFile = meeting.getFile();
        Long minutesFileId = (minutesFile != null) ? minutesFile.getFileId() : null;
        // 회의록 파일 제외한 회의 파일 리스트
        List<FileDto> allFiles = fileService.getMeetingFiles(id);
        List<FileDto> fileList = allFiles.stream()
                .filter(f -> !Objects.equals(f.getFileId(), minutesFileId))
                .toList();

        // 회의록
        FileDto meetingMinutes = (minutesFile != null) ? FileDto.fromEntity(minutesFile) : null;

        // 부서들
        List<String> departmentNames = meetingDepartmentService.getDepartmentName(meeting);

        // 요청자의 수정 권한 여부
        boolean isEditPermitted = meetingMembers.stream()
                .filter(mm -> mm.getMember().getId().equals(memberId))
                .anyMatch(MeetingMember::isPermitted);
        // 참여자
        List<MeetingMemberDto> participantList = meetingMembers.stream()
                .map(MeetingMemberDto::fromEntity)
                .toList();

        return MeetingDto.fromEntity(
                meeting,
                host,
                departmentNames,
                fileList,
                meetingMinutes,
                isEditPermitted,
                participantList
        );
    }

    public void updateReadStatus(Long id, Long memberId) {
        Member member = memberService.getMemberById(memberId);
        Meeting meeting = getMeetingById(id);
        MeetingMember meetingMember = meetingMemberService.getMember(meeting, member);
        if (meetingMember.isRead()) {
            return;
        }
        meetingMember.updateIsRead(true);
    }

    public Meeting updateIssue(Long id, MeetingFormDto meetingFormDto, List<MultipartFile> newFiles, List<Long> removeFileIds) {
        Meeting meeting = getMeetingById(id);
        Category category = categoryService.getCategoryById(meetingFormDto.getCategoryId());

        Long issueId = meetingFormDto.getIssueId();
        Issue issue = null;
        if (issueId != null) {
            issue = issueService.getIssueById(issueId);
        }
        meeting.update(meetingFormDto, category, issue);

        // 상태가 완료일 경우, 마감일 자동입력
        if (meetingFormDto.getStatus().equals(String.valueOf(Status.COMPLETED))) {
            meeting.updateEndDate();
        }

        // 회의 부서 엔티티 삭제 후 추가
        meetingDepartmentService.deleteMeetingDepartment(meeting);
        List<Long> departmentIds = meetingFormDto.getDepartmentIds();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            meetingDepartmentService.saveDepartment(id, departmentIds);
        } else {
            // 상태가 완료가 아닌 경우 마감일 제거
            meeting.clearEndDate();
        }

        // 회의 참여자 엔티티 삭제 후 추가
        meetingMemberService.deleteMeetingMember(meeting);
        List<MeetingMemberDto> meetingMemberDtos = meetingFormDto.getMembers();
        if (meetingMemberDtos != null && !meetingMemberDtos.isEmpty()) {
            meetingMemberService.saveMeetingMember(id, meetingMemberDtos);
        }

        // 파일 업데이트
        if ((newFiles != null && !newFiles.isEmpty()) || (removeFileIds != null && !removeFileIds.isEmpty())) {
            fileService.updateFiles(id, newFiles, removeFileIds, TargetType.MEETING);
        }
        return meeting;
    }

    public void deleteMeeting(Long id) {
        Meeting meeting = getMeetingById(id);
        meeting.deleteMeeting();
    }

    public void saveMeetingMinutes(Long id, List<MultipartFile> multipartFiles) {
        Meeting meeting = getMeetingById(id);
        // 회의록 저장
        List<File> file = fileService.uploadFiles(id, multipartFiles, TargetType.MEETING);

        if (file.isEmpty()) {
            throw new RuntimeException("업로드된 회의록 파일이 없습니다.");
        }
        // 회의록 등록, 상태 완료, 마감일 저장.
        meeting.saveMeetingMinutes(file.getFirst());

    }

    public void deleteMeetingMinutes(Long meetingId, Long fileId) {
        Meeting meeting = getMeetingById(meetingId);
        File file = fileService.getFileById(fileId);
        fileService.deleteFiles(List.of(file));
        meeting.deleteMeetingMinutes();
    }

    // 미팅 조회
    public Page<MeetingListDto> findAll(Pageable pageable) {
        return meetingRepository.findByIsDelFalse(pageable)
                .map(this::toMeetingListDto);
    }

    //회의 캘린더 조회
    public List<MeetingListDto> findByDateBetween(
            int year, int month
    ) {
        //LocalDate로 변경
        LocalDate startDate  = LocalDate.of(year, month, 1);//시작 날짜 ex) 2025-12-01
        LocalDate endDate  = startDate.withDayOfMonth(startDate.lengthOfMonth()); //ex? 12월의 마지막 날을 일자 부분에 삽입 => 2025-12-31

        //LocalDateTime 으로 변경
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Meeting> meetings = meetingRepository.findByStartDateBetween(startDateTime, endDateTime);

        return meetings.stream()
                .map(this::toMeetingListDto)
                .toList();

    }



    // issueId로 관련 회의 조회
    public Page<MeetingListDto> getMeetingRelatedIssue(Long issueId, Pageable pageable) {
        Issue issue = issueService.getIssueById(issueId);
        return meetingRepository.findByIssueAndIsDelFalse(issue, pageable)
                .map(this::toMeetingListDto);
    }

    // Meeting → MeetingListDto 변환
    private MeetingListDto toMeetingListDto(Meeting meeting) {
        MeetingMember host = meetingMemberService.getHost(meeting);
        String hostName = (host != null) ? host.getMember().getName() : null;
        String hostJPName = (host != null && host.getMember().getJobPosition() != null)
                ? host.getMember().getJobPosition().getName()
                : null;
        List<String> departmentName = meetingDepartmentService.getDepartmentName(meeting);
        return MeetingListDto.fromEntity(meeting, departmentName, hostName, hostJPName);
    }

    // meetingId로 회의 조회
    public Meeting getMeetingById(Long meetingId) {
        return meetingRepository.findById(meetingId).orElseThrow(() -> new EntityNotFoundException("회의가 존재하지 않습니다."));
    }
}
