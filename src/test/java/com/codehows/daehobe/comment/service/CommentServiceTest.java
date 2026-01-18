package com.codehows.daehobe.comment.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.comment.dto.CommentDto;
import com.codehows.daehobe.comment.dto.CommentRequest;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.comment.repository.CommentRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.issue.service.IssueService;
import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.masterData.service.SetNotificationService;
import com.codehows.daehobe.meeting.service.MeetingService;
import com.codehows.daehobe.member.service.MemberService;
import com.codehows.daehobe.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy; // spy import
import java.util.Arrays; // Arrays import

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private MemberService memberService;
    @Mock private IssueService issueService;
    @Mock private MeetingService meetingService;
    @Mock private MentionService mentionService;
    @Mock private FileService fileService;
    @Mock private NotificationService notificationService;
    @Mock private SetNotificationService setNotificationService;

    @InjectMocks
    private CommentService commentService;

    private Member testMember;
    private Comment testComment;
    private CommentRequest testCommentRequest;
    private SetNotificationDto notificationSetting;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L) // memberId 대신 id 사용
                .name("테스터")
                .loginId("testLogin")
                .password("testPassword")
                .phone("010-1234-5678")
                .email("test@email.com")
                .isEmployed(true)
                .build();
        
        testComment = Comment.builder()
                .id(1L)
                .targetId(10L)
                .targetType(TargetType.ISSUE)
                .content("테스트 댓글")
                .member(testMember)
                .isDel(false)
                .build();
        ReflectionTestUtils.setField(testComment, "createdAt", LocalDateTime.now()); // createdAt 수동 설정

        testCommentRequest = new CommentRequest(
                10L, // targetId
                TargetType.ISSUE, // targetType
                "새 댓글",
                Collections.singletonList(2L), // mentionedMemberIds
                Collections.emptyList() // removeFileIds
        );
        notificationSetting = new SetNotificationDto(true, true, true, true, true);
    }

    @Test
    @DisplayName("성공: 이슈 ID로 댓글 목록 조회")
    void getCommentsByIssueId_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Comment> commentPage = new PageImpl<>(Collections.singletonList(testComment), pageable, 1);
        when(commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(anyLong(), eq(TargetType.ISSUE), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fileService.findFirstByTargetIdAndTargetType(anyLong(), any(TargetType.class))).thenReturn(null);
        when(fileService.getCommentFiles(anyLong())).thenReturn(Collections.emptyList());
        
        // when
        Page<CommentDto> result = commentService.getCommentsByIssueId(10L, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("테스트 댓글");
    }

    @Test
    @DisplayName("성공: 이슈 댓글 작성 (멘션, 파일 포함)")
    void createIssueComment_FullScenario() {
        // given
        Long issueId = 10L;
        Long memberId = testMember.getId();
        List<MultipartFile> files = Collections.singletonList(new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes()));
        
        when(issueService.getIssueDtl(eq(issueId), eq(memberId))).thenReturn(null); // 실제 반환값은 사용 안 함
        when(memberService.getMemberById(eq(memberId))).thenReturn(testMember);
        when(commentRepository.save(any(Comment.class))).thenReturn(testComment);
        when(setNotificationService.getSetting()).thenReturn(notificationSetting);
        doNothing().when(mentionService).saveMentions(any(Comment.class), anyList());
        doNothing().when(notificationService).notifyMembers(anyList(), anyLong(), anyString(), anyString());
        when(fileService.uploadFiles(anyLong(), anyList(), any(TargetType.class))).thenReturn(Collections.emptyList());

        // when
        Comment result = commentService.createIssueComment(issueId, testCommentRequest, memberId, files);

        // then
        assertThat(result.getContent()).isEqualTo("테스트 댓글");
        verify(commentRepository).save(any(Comment.class));
        verify(mentionService).saveMentions(eq(testComment), eq(testCommentRequest.getMentionedMemberIds()));
        verify(notificationService).notifyMembers(anyList(), eq(memberId), anyString(), anyString());
        verify(fileService).uploadFiles(eq(testComment.getId()), eq(files), eq(TargetType.COMMENT));
    }

    @Test
    @DisplayName("성공: 회의 ID로 댓글 목록 조회")
    void getCommentsByMeetingId_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Comment> commentPage = new PageImpl<>(Collections.singletonList(testComment), pageable, 1);
        when(commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(anyLong(), eq(TargetType.MEETING), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fileService.findFirstByTargetIdAndTargetType(anyLong(), any(TargetType.class))).thenReturn(null);
        when(fileService.getCommentFiles(anyLong())).thenReturn(Collections.emptyList());
        
        // when
        Page<CommentDto> result = commentService.getCommentsByMeetingId(10L, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("테스트 댓글");
    }

    @Test
    @DisplayName("성공: 회의 댓글 작성 (멘션, 파일 포함)")
    void createMeetingComment_FullScenario() {
        // given
        Long meetingId = 10L;
        Long memberId = testMember.getId();
        List<MultipartFile> files = Collections.singletonList(new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes()));
        
        when(meetingService.getMeetingDtl(eq(meetingId), eq(memberId))).thenReturn(null); // 실제 반환값은 사용 안 함
        when(memberService.getMemberById(eq(memberId))).thenReturn(testMember);
        when(commentRepository.save(any(Comment.class))).thenReturn(testComment);
        when(setNotificationService.getSetting()).thenReturn(notificationSetting);
        doNothing().when(mentionService).saveMentions(any(Comment.class), anyList());
        doNothing().when(notificationService).notifyMembers(anyList(), anyLong(), anyString(), anyString());
        when(fileService.uploadFiles(anyLong(), anyList(), any(TargetType.class))).thenReturn(Collections.emptyList());

        // when
        Comment result = commentService.createMeetingComment(meetingId, testCommentRequest, memberId, files);

        // then
        assertThat(result.getContent()).isEqualTo("테스트 댓글");
        verify(commentRepository).save(any(Comment.class));
        verify(mentionService).saveMentions(eq(testComment), eq(testCommentRequest.getMentionedMemberIds()));
        verify(notificationService).notifyMembers(anyList(), eq(memberId), anyString(), anyString());
        verify(fileService).uploadFiles(eq(testComment.getId()), eq(files), eq(TargetType.COMMENT));
    }

    @Test
    @DisplayName("성공: 댓글 수정 (멘션, 파일 변경 포함)")
    void updateComment_FullScenario() {
        // given
        Long commentId = testComment.getId();
        CommentRequest updateRequest = new CommentRequest(
                testComment.getTargetId(),
                testComment.getTargetType(),
                "수정된 댓글",
                Arrays.asList(3L, 4L),
                Collections.emptyList()
        );
        List<MultipartFile> newFiles = Collections.singletonList(new MockMultipartFile("newFile", "new.txt", "text/plain", "new data".getBytes()));
        List<Long> removeFileIds = Collections.singletonList(1L);
        
        Comment spyComment = spy(testComment); // update 호출 검증용
        
        when(commentRepository.findById(eq(commentId))).thenReturn(Optional.of(spyComment));
        when(mentionService.getMentionedMemberIds(anyLong())).thenReturn(Collections.singletonList(2L)); // 기존 멘션 ID
        doNothing().when(mentionService).updateMentions(any(Comment.class), anyList());
        when(setNotificationService.getSetting()).thenReturn(notificationSetting);
        when(memberService.getMemberById(eq(spyComment.getMember().getId()))).thenReturn(testMember); // 댓글 작성자
        doNothing().when(notificationService).notifyMembers(anyList(), anyLong(), anyString(), anyString());
        doNothing().when(fileService).updateFiles(anyLong(), anyList(), anyList(), any(TargetType.class));

        // when
        Comment result = commentService.updateComment(commentId, updateRequest, newFiles, removeFileIds);

        // then
        assertThat(result.getContent()).isEqualTo("수정된 댓글");
        verify(spyComment).update(any(CommentRequest.class));
        verify(mentionService).updateMentions(eq(spyComment), eq(updateRequest.getMentionedMemberIds()));
        verify(notificationService).notifyMembers(anyList(), eq(testMember.getId()), anyString(), anyString());
        verify(fileService).updateFiles(eq(commentId), eq(newFiles), eq(removeFileIds), eq(TargetType.COMMENT));
    }

    @Test
    @DisplayName("성공: 댓글 삭제")
    void deleteComment_Success() {
        // given
        Long commentId = testComment.getId();
        Comment spyComment = spy(testComment);
        when(commentRepository.findById(eq(commentId))).thenReturn(Optional.of(spyComment));

        // when
        commentService.deleteComment(commentId);

        // then
        verify(spyComment).delete();
    }

    @Test
    @DisplayName("성공: ID로 댓글 조회")
    void getCommentById_Success() {
        // given
        Long commentId = testComment.getId();
        when(commentRepository.findById(eq(commentId))).thenReturn(Optional.of(testComment));

        // when
        Comment result = commentService.getCommentById(commentId);

        // then
        assertThat(result.getContent()).isEqualTo("테스트 댓글");
    }

    @Test
    @DisplayName("실패: ID로 댓글 조회 (댓글 없음)")
    void getCommentById_NotFound() {
        // given
        Long commentId = 99L;
        when(commentRepository.findById(eq(commentId))).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> commentService.getCommentById(commentId));
    }
}
