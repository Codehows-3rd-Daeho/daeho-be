package com.codehows.daehobe.meeting.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.Status;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.service.IssueService;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.service.CategoryService;
import com.codehows.daehobe.masterData.service.SetNotificationService;
import com.codehows.daehobe.meeting.dto.MeetingFormDto;
import com.codehows.daehobe.meeting.dto.MeetingMemberDto;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.repository.MeetingMemberRepository;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.codehows.daehobe.member.service.MemberService;
import com.codehows.daehobe.notification.service.NotificationService;
import com.codehows.daehobe.stt.service.STTService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class MeetingServiceTest {

    @Mock private CategoryService categoryService;
    @Mock private MeetingRepository meetingRepository;
    @Mock private FileService fileService;
    @Mock private MeetingDepartmentService meetingDepartmentService;
    @Mock private MeetingMemberService meetingMemberService;
    @Mock private IssueService issueService;
    @Mock private MemberService memberService;
    @Mock private NotificationService notificationService;
    @Mock private SetNotificationService setNotificationService;
    @Mock private STTService sttService;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private MemberRepository memberRepository;

    private MeetingService meetingService;

    private Meeting testMeeting;
    private Category testCategory;
    private Issue testIssue;
    private MeetingFormDto testMeetingFormDto;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
            meetingRepository, fileService, issueService,
            categoryService, meetingDepartmentService, meetingMemberService,
            notificationService, setNotificationService, memberService,
            sttService, meetingMemberRepository, memberRepository
        );

        testCategory = Category.builder().id(1L).name("정기회의").build();
        testIssue = Issue.builder().id(1L).title("테스트 이슈").build();
        testMeeting = Meeting.builder()
                .id(1L)
                .title("테스트 회의")
                .status(Status.IN_PROGRESS)
                .category(testCategory)
                .build();
        testMeetingFormDto = MeetingFormDto.builder()
                .title("테스트 회의")
                .categoryId(1L)
                .status(Status.IN_PROGRESS.name())
                .members(Collections.singletonList(new MeetingMemberDto()))
                .build();
    }

    @Test
    @DisplayName("성공: 회의 생성 (이슈 포함)")
    void createMeeting_WithIssue_Success() {
        // given
        MeetingFormDto dtoWithIssue = MeetingFormDto.builder()
                .title(testMeetingFormDto.getTitle())
                .categoryId(testMeetingFormDto.getCategoryId())
                .status(testMeetingFormDto.getStatus())
                .members(testMeetingFormDto.getMembers())
                .issueId(1L)
                .build();
        Meeting meetingWithIssue = Meeting.builder()
                .id(1L).title("테스트 회의").status(Status.IN_PROGRESS)
                .category(testCategory).issue(testIssue).build();
        when(categoryService.getCategoryById(anyLong())).thenReturn(testCategory);
        when(issueService.getIssueById(anyLong())).thenReturn(testIssue);
        when(meetingRepository.save(any(Meeting.class))).thenReturn(meetingWithIssue);
        when(setNotificationService.getSetting()).thenReturn(new com.codehows.daehobe.masterData.dto.SetNotificationDto(false, false, false, false, false));

        // when
        Meeting createdMeeting = meetingService.createMeeting(dtoWithIssue, null, "1");

        // then
        assertThat(createdMeeting).isNotNull();
        assertThat(createdMeeting.getIssue()).isEqualTo(testIssue);
        verify(meetingRepository).save(any(Meeting.class));
        verify(meetingMemberService).saveMeetingMember(anyLong(), anyList());
    }

    @Test
    @DisplayName("성공: 회의 생성 (이슈 없음)")
    void createMeeting_NoIssue_Success() {
        // given
        MeetingFormDto dtoNoIssue = MeetingFormDto.builder()
                .title(testMeetingFormDto.getTitle())
                .categoryId(testMeetingFormDto.getCategoryId())
                .status(testMeetingFormDto.getStatus())
                .members(testMeetingFormDto.getMembers())
                .issueId(0L)
                .build();
        when(categoryService.getCategoryById(anyLong())).thenReturn(testCategory);
        when(meetingRepository.save(any(Meeting.class))).thenReturn(testMeeting);
        when(setNotificationService.getSetting()).thenReturn(new com.codehows.daehobe.masterData.dto.SetNotificationDto(false, false, false, false, false));

        // when
        Meeting createdMeeting = meetingService.createMeeting(dtoNoIssue, null, "1");

        // then
        assertThat(createdMeeting).isNotNull();
        assertThat(createdMeeting.getIssue()).isNull();
        verify(issueService, never()).getIssueById(anyLong());
    }
    
    @Test
    @DisplayName("성공: 회의 삭제")
    void deleteMeeting_Success() {
        // given
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));
        
        // when
        Meeting deletedMeeting = meetingService.deleteMeeting(1L);
        
        // then
        assertThat(deletedMeeting.isDel()).isTrue();
    }
    
    @Test
    @DisplayName("실패: 회의 삭제 (회의 없음)")
    void deleteMeeting_NotFound() {
        // given
        when(meetingRepository.findById(99L)).thenReturn(Optional.empty());
        
        // when & then
        assertThrows(EntityNotFoundException.class, () -> meetingService.deleteMeeting(99L));
    }

    @Test
    @DisplayName("성공: ID로 회의 조회")
    void getMeetingById_Success() {
        // given
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(testMeeting));
        
        // when
        Meeting foundMeeting = meetingService.getMeetingById(1L);
        
        // then
        assertThat(foundMeeting).isEqualTo(testMeeting);
    }
}
