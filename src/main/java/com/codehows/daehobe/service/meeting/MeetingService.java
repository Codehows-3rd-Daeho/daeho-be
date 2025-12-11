package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.repository.meeting.MeetingDepartmentRepository;
import com.codehows.daehobe.repository.meeting.MeetingMemberRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final IssueRepository issueRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MeetingDepartmentRepository meetingDepartmentRepository;
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;

    public Meeting createMeeting(MeetingFormDto meetingFormDto, List<MultipartFile> multipartFiles) {


        // 1. DTO에서 categoryId를 가져와 실제 엔티티 조회
        Category categoryId = categoryService.getCategoryById(meetingFormDto.getCategoryId());

        //2. Dto에서 issueId를 가져와 실제 엔티티 조회
//        Issue issue = issueRepository.findById(meetingFormDto.getIssueId())
//                .orElseThrow(() -> new RuntimeException("해당 이슈가 존재하지 않습니다."));


        Long issueId = meetingFormDto.getIssueId();
        Issue issue = null;//이슈 없을 시 null값 사용
        if (issueId != 0) {
            issue = issueRepository.findById(issueId)
                    .orElseThrow(() -> new RuntimeException("해당 이슈가 존재하지 않습니다."));
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
        Meeting meeting = meetingRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회의가 존재하지 않습니다."));
        // 주관자
        MeetingMember host = meetingMemberRepository.findByMeetingAndIsHost(meeting, true);
        String hostName = (host != null) ? host.getMember().getName() : null;
        String hostJPName = (host != null && host.getMember().getJobPosition() != null)
                ? host.getMember().getJobPosition().getName()
                : null;

        // 회의록
        File minutesFile = meeting.getFile();
        Long minutesFileId = (minutesFile != null) ? minutesFile.getFileId() : null;
        // 회의 파일
        List<FileDto> fileDtoList = fileRepository.findByTargetIdAndTargetType(id, TargetType.MEETING)
                .stream()
                .filter(f -> !Objects.equals(f.getFileId(), minutesFileId))
                .map(FileDto::fromEntity)
                .toList();

        // 관련 이슈
        Issue issue = meeting.getIssue();
        Long issueId = (issue != null) ? issue.getId() : null; // 이슈 id
        String issueTitle = (issue != null) ? issue.getTitle() : null; // 이슈 제목

        // 카테고리
        String categoryName = meeting.getCategory().getName();
        // 부서들
        List<String> departmentNames = meetingDepartmentRepository.findByMeeting(meeting)
                .stream()
                .map(dpt -> dpt.getDepartment().getName())
                .toList();

        // 회의록
        FileDto meetingMinutes = (minutesFile != null) ? FileDto.fromEntity(minutesFile) : null;

        // 유저가 해당 게시글의 수정,삭제 권한을 갖고있는지.
        Member member = memberRepository.findById(memberId).orElseThrow(EntityNotFoundException::new);
        MeetingMember meetingMember = meetingMemberRepository.findByMeetingAndMember(meeting, member).orElse(null);
        boolean isEditPermitted = meetingMember != null && meetingMember.isPermitted(); //이 사용자에게 수정 권한이 있을 때만 true

        // 참여자
        List<MeetingMemberDto> participantList = meetingMemberRepository.findByMeeting(meeting)
                .stream()
                .map(MeetingMemberDto::fromEntity)
                .toList();

        return MeetingDto.builder()
                .title(meeting.getTitle())
                .content(meeting.getContent())
                .fileList(fileDtoList)
                .status(meeting.getStatus().toString())
                .hostName(hostName)
                .hostJPName(hostJPName)
                .issueId(issueId)
                .issueTitle(issueTitle)
                .startDate(meeting.getStartDate())
                .endDate(meeting.getEndDate())
                .categoryName(categoryName)
                .departmentName(departmentNames)
                .meetingMinutes(meetingMinutes)
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .del(meeting.isDel())
                .editPermitted(isEditPermitted)
                .participantList(participantList)
                .build();
    }

    public void updateReadStatus(Long id, Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(EntityNotFoundException::new);
        Meeting meeting = meetingRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        MeetingMember meetingMember = meetingMemberRepository.findByMeetingAndMember(meeting, member).orElseThrow(EntityNotFoundException::new);
        if (meetingMember.isRead()) {
            return;
        }
        meetingMember.updateIsRead(true);
    }


    public void deleteMeeting(Long id) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회의가 존재하지 않습니다."));
        meeting.deleteMeeting();
    }

    public void saveMeetingMinutes(Long id, List<MultipartFile> multipartFiles) {
        Meeting meeting = meetingRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        // 회의록 저장
        List<File> file = fileService.uploadFiles(id, multipartFiles, TargetType.MEETING);

        if (file.isEmpty()) {
            throw new RuntimeException("업로드된 회의록 파일이 없습니다.");
        }
        // 회의록 등록, 상태 완료, 마감일 저장.
        meeting.saveMeetingMinutes(file.getFirst());

    }

    public void deleteMeetingMinutes(Long meetingId, Long fileId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(EntityNotFoundException::new);
        File file = fileRepository.findById(fileId).orElseThrow(EntityNotFoundException::new);
        fileService.deleteFiles(List.of(file));
        meeting.deleteMeetingMinutes();
    }

}
