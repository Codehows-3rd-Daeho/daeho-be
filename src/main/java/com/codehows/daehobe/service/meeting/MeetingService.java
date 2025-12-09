package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.CategoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    public Meeting createMeeting(MeetingDto meetingDto , List<MultipartFile> multipartFiles) {


        // 1. DTO에서 categoryId를 가져와 실제 엔티티 조회
        Category categoryId = categoryService.getCategoryById(meetingDto.getCategoryId());

        //2. Dto에서 issueId를 가져와 실제 엔티티 조회
        Issue issue = issueRepository.findById(meetingDto.getIssueId())
                .orElseThrow(() -> new RuntimeException("해당 이슈가 존재하지 않습니다."));


        //entity에 dto로 받은 값 넣기(builder 사용)
        Meeting saveMeeting = Meeting.builder()
                .title(meetingDto.getTitle())
                .content(meetingDto.getContent())
                .status(Status.valueOf(meetingDto.getStatus()))
                .issueId(issue)
                .startDate(meetingDto.getStartDate())
                .endDate(meetingDto.getEndDate())
                .categoryId(categoryId)
                .isDel(meetingDto.getIsDel())
                .build();

        meetingRepository.save(saveMeeting);

        //회의 부서
        // 1. DTO에서 부서 이름 목록 (List<Long>) 추출
        List<Long> departmentIds = meetingDto.getDepartmentIds();
        //2. 부서 저장 서비스 호출
        if(departmentIds != null && !departmentIds.isEmpty()) {
            meetingDepartmentService.saveDepartment(saveMeeting.getMeetingId(), departmentIds);
        }

        //회의 참여자
        //  1. DTO에서 참여자 이름 목록 (List<String>) 추출
        List<MeetingMemberDto> issueMemberDtos = meetingDto.getMembers();
        //2. 참여자 저장 서비스 호출
        if (issueMemberDtos != null && !issueMemberDtos.isEmpty()) {
            meetingMemberService.saveMeetingMember(saveMeeting.getMeetingId(), issueMemberDtos);

        }

        //파일 저장
        if(multipartFiles != null) {
            fileService.uploadFiles(saveMeeting.getMeetingId(),  multipartFiles, TargetType.MEETING);
        }

        return saveMeeting;
    }
}
