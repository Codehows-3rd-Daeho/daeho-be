package com.codehows.daehobe.service.comment;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.commnet.CommentRepository;
import com.codehows.daehobe.service.issue.IssueService;
import com.codehows.daehobe.service.meeting.MeetingService;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final MemberService memberService;
    private final IssueService issueService;
    private final MeetingService meetingService;
    private final MentionService mentionService;

    // 이슈 댓글 호출
    public  Page<CommentDto> getCommentsByIssueId(Long issueId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetType(issueId, TargetType.ISSUE, false, pageable);

        return comments.map(this::toCommentDto);
    }

    // 이슈 댓글 작성
    public CommentDto createIssueComment(Long issueId, CommentRequest dto, Long memberId) {
        issueService.getIssueDtl(issueId, memberId);
        Member writer = memberService.getMemberById(memberId);

        Comment saved = createAndSaveComment(
                issueId,
                TargetType.ISSUE,
                dto.getContent(),
                writer);

        if (dto.getMentionedMemberIds() != null && !dto.getMentionedMemberIds().isEmpty()) {
            mentionService.saveMentions(saved, dto.getMentionedMemberIds());
        }

        return toCommentDto(saved);
    }

    // 회의 댓글 호출
    public  Page<CommentDto> getCommentsByMeetingId(Long meetingId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetType(meetingId, TargetType.MEETING, false, pageable);

        return comments.map(this::toCommentDto);
    }

    // 회의 댓글 작성
    public CommentDto createMeetingComment(Long meetingId, CommentRequest dto, Long memberId) {
        meetingService.getMeetingDtl(meetingId, memberId);
        Member writer = memberService.getMemberById(memberId);

        Comment saved = createAndSaveComment(
                meetingId,
                TargetType.MEETING,
                dto.getContent(),
                writer);

        if (dto.getMentionedMemberIds() != null && !dto.getMentionedMemberIds().isEmpty()) {
            mentionService.saveMentions(saved, dto.getMentionedMemberIds());
        }

        return toCommentDto(saved);
    }

    // =======================
    // 공통 로직

    // 조회
    private CommentDto toCommentDto(Comment comment) {
        Member writer = comment.getMember();
        String writerName = writer.getName();

        String writerJPName = writer.getJobPosition() != null
                ? writer.getJobPosition().getName()
                : null;

        return CommentDto.fromComment(comment,writerName, writerJPName);
    }

    // 등록
    private Comment createAndSaveComment(
            Long targetId,
            TargetType targetType,
            String content,
            Member writer
    ) {
        Comment comment = Comment.builder()
                .targetId(targetId)
                .targetType(targetType)
                .content(content)
                .member(writer)
                .isDel(false)
                .build();

        return commentRepository.save(comment);
    }
}
