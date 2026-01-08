package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.aop.TrackChanges;
import com.codehows.daehobe.aop.TrackMemberChanges;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.FilterDto;
import com.codehows.daehobe.dto.masterData.SetNotificationDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.webpush.KafkaNotificationMessageDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.issue.IssueService;
import com.codehows.daehobe.service.masterData.CategoryService;
import com.codehows.daehobe.service.masterData.SetNotificationService;
import com.codehows.daehobe.service.member.MemberService;
import com.codehows.daehobe.service.stt.STTService;
import com.codehows.daehobe.service.webpush.NotificationService;
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
import java.util.stream.Collectors;

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
    private final NotificationService notificationService;
    private final SetNotificationService setNotificationService;
    private final STTService sttService;


    @TrackChanges(type = ChangeType.CREATE, target = TargetType.MEETING)
    public Meeting createMeeting(MeetingFormDto meetingFormDto, List<MultipartFile> multipartFiles, String writerId) {

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
                .isDel(Boolean.TRUE.equals(meetingFormDto.getIsDel()))
                .isPrivate(Boolean.TRUE.equals(meetingFormDto.getIsPrivate()))
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
        List<MeetingMemberDto> meetingMemberDtos = meetingFormDto.getMembers();
        //2. 참여자 저장 서비스 호출
        if (meetingMemberDtos != null && !meetingMemberDtos.isEmpty()) {
            meetingMemberService.saveMeetingMember(saveMeeting.getId(), meetingMemberDtos);

        }

        //파일 저장
        if (multipartFiles != null) {
            fileService.uploadFiles(saveMeeting.getId(), multipartFiles, TargetType.MEETING);
        }


        // 알림 발송
        SetNotificationDto settingdto = setNotificationService.getSetting();
        if (meetingMemberDtos != null && !meetingMemberDtos.isEmpty() && settingdto.isMeetingCreated()) {
            notificationService.notifyMembers(meetingMemberDtos.stream()
                            .map(MeetingMemberDto::getId)
                            .toList(),
                    Long.valueOf(writerId),
                    "새 회의가 등록되었습니다 \n" + saveMeeting.getTitle(),
                    "/meeting/" + saveMeeting.getId()
            );
        }

        return saveMeeting;
    }

    public MeetingDto getMeetingDtl(Long id, Long memberId) {
        // Meeting 조회 (연관 엔티티 포함)
        Meeting meeting = meetingRepository.findDetailById(id)
                .orElseThrow(() -> new RuntimeException("회의가 존재하지 않습니다."));

        // 해당 회의의 모든 참여자
        List<MeetingMember> meetingMembers = meeting.getMeetingMembers();

        // 주관자
        MeetingMember host = meetingMembers.stream()
                .filter(MeetingMember::isHost)
                .findFirst()
                .orElse(null);

        // 요청자의 수정 권한 여부
        boolean isEditPermitted = meetingMembers.stream()
                .anyMatch(mm -> mm.getMember().getId().equals(memberId) && mm.isPermitted());

        // 참여자 DTO 변환
        List<MeetingMemberDto> participantList = meetingMembers.stream()
                .map(MeetingMemberDto::fromEntity)
                .toList();

        // 회의 파일
        File minutesFile = meeting.getFile();
        Long minutesFileId = (minutesFile != null) ? minutesFile.getFileId() : null;

        List<FileDto> allFiles = fileService.getMeetingFiles(id);
        List<FileDto> fileList = allFiles.stream()
                .filter(f -> !Objects.equals(f.getFileId(), minutesFileId))
                .toList();

        // 회의록 DTO
        FileDto meetingMinutes = (minutesFile != null) ? FileDto.fromEntity(minutesFile) : null;

        // 부서 이름
        List<String> departmentNames = meetingDepartmentService.getDepartmentName(meeting);

        String totalSummary = sttService.getSTTsByMeetingId(id, memberId)
                .stream()
                .map(STTDto::getSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));

        return MeetingDto.fromEntity(
                meeting,
                host,
                departmentNames,
                fileList,
                meetingMinutes,
                isEditPermitted,
                participantList,
                totalSummary
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

    @TrackChanges(type = ChangeType.UPDATE, target = TargetType.MEETING)
    @TrackMemberChanges(target = TargetType.MEETING)
    public Meeting updateMeeting(Long id, MeetingFormDto meetingFormDto, List<MultipartFile> newFiles, List<Long> removeFileIds, String writerId) {
        Meeting meeting = getMeetingById(id);
        Category category = categoryService.getCategoryById(meetingFormDto.getCategoryId());

        Long issueId = meetingFormDto.getIssueId();
        Issue issue = null;
        if (issueId != null) {
            issue = issueService.getIssueById(issueId);
        }
        Status beforeStatus = meeting.getStatus(); // 수정전 상태
        meeting.update(meetingFormDto, category, issue);
        Status afterStatus = meeting.getStatus(); // 수정후 상태

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

        // 상태 변경 알림
        SetNotificationDto settingdto = setNotificationService.getSetting();// 알림 설정 가져오기
        if (!beforeStatus.equals(afterStatus) && meetingMemberDtos != null && settingdto.isMeetingStatus()) {
            notificationService.notifyMembers(meetingMemberDtos.stream()
                            .map(MeetingMemberDto::getId)
                            .toList(),
                    Long.valueOf(writerId),
                    "회의 상태가 변경되었습니다 \n" +
                            beforeStatus.getLabel() + " → " + afterStatus.getLabel(),
                    "/meeting/" + meeting.getId()
            );
        }
        return meeting;
    }

    public Meeting deleteMeeting(Long id) {
        Meeting meeting = getMeetingById(id);
        meeting.deleteMeeting();
        return meeting;
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
        meeting.saveMeetingMinutes(null);
    }

    // 회의 조회
    public Page<MeetingListDto> findAll(FilterDto filter, Pageable pageable, Long memberId) {
        LocalDateTime startDt = (filter.getStartDate() != null) ?
                filter.getStartDate().atStartOfDay() : null;
        LocalDateTime endDt = (filter.getEndDate() != null) ?
                filter.getEndDate().atTime(23, 59, 59) : null;

        Page<Meeting> meetings = meetingRepository.findMeetingsWithFilter(filter, memberId,startDt, endDt,false, pageable);
        return meetings.map(this::toMeetingListDto);
    }

    //회의 캘린더 조회
    public List<MeetingListDto> findByDateBetween(
            Long memberId,int year, int month
    ) {
        //LocalDate로 변경
        LocalDate startDate = LocalDate.of(year, month, 1);//시작 날짜 ex) 2025-12-01
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth()); //ex? 12월의 마지막 날을 일자 부분에 삽입 => 2025-12-31

        //LocalDateTime 으로 변경
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Meeting> meetings = meetingRepository.findCalendarMeetings(memberId, startDateTime, endDateTime);

        return meetings.stream()
                .map(this::toMeetingListDto)
                .toList();

    }

    //나의 업무 캘린더 조회
    public List<MeetingListDto> findByDateBetweenForMember(Long memberId, int year, int month) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.withDayOfMonth(start.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59);


        System.out.println(" =============================================================");
        System.out.println(" findByDateBetweenForMember memberId: " + memberId);
        System.out.println(" findByDateBetweenForMember year: " + year);
        System.out.println(" findByDateBetweenForMember month: " + month);
        System.out.println(" =============================================================");

        List<MeetingMember> meetings = meetingMemberService.findMeetingsByMemberAndDate(memberId, start, end);

        System.out.println(" findByDateBetweenForMember meetings: " + meetings);


        return meetings.stream()
                .map(MeetingMember::getMeeting)
                .map(this::toMeetingListDto) // 엔티티 → DTO 변환
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

    // 나의 업무 회의 조회 + 검색
    public Page<MeetingListDto> getMeetingsForMember(Long memberId, FilterDto filter, Pageable pageable) {
        LocalDateTime startDt = (filter.getStartDate() != null) ?
                filter.getStartDate().atStartOfDay() : null;
        LocalDateTime endDt = (filter.getEndDate() != null) ?
                filter.getEndDate().atTime(23, 59, 59) : null;

        Page<Meeting> meetings = meetingRepository.findMeetingsWithFilter(filter, memberId, startDt, endDt,true, pageable);
        return meetings.map(this::toMeetingListDto);
    }
}
