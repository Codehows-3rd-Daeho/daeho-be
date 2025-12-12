package com.codehows.daehobe.service.comment;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.repository.commnet.CommentRepository;
import com.codehows.daehobe.service.issue.IssueService;
import com.codehows.daehobe.service.meeting.MeetingService;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final MemberService memberService;
    private final IssueService issueService;
    private final MeetingService meetingService;

    // 이슈 댓글 호출
    public  Page<CommentDto> getCommentsByIssueId(Long issueId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetType(issueId, TargetType.ISSUE, false, pageable);

        return comments.map(comment -> {
            String writerName = memberService.getMemberNameById(comment.getCreatedBy());
            return CommentDto.fromComment(comment, writerName);
        });
    }

    // 이슈 댓글 작성
    public CommentDto createIssueComment(Long issueId, CommentRequest dto, Long memberId) {
        issueService.getIssueDtl(issueId, memberId); // 존재 검증

        Comment comment = Comment.builder()
                .targetId(issueId)
                .targetType(TargetType.ISSUE)
                .content(dto.getContent())
                .isDel(false)
                .build();

        Comment saved = commentRepository.save(comment);

        String writerName = memberService.getMemberNameById(saved.getCreatedBy());

        return CommentDto.fromComment(saved, writerName);
    }

    // 회의 댓글 호출
    public  Page<CommentDto> getCommentsByMeetingId(Long meetingId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetType(meetingId, TargetType.MEETING, false, pageable);

        return comments.map(comment -> {
            String writerName = memberService.getMemberNameById(comment.getCreatedBy());
            return CommentDto.fromComment(comment, writerName);
        });
    }

    // 회의 댓글 작성
    public CommentDto createMeetingComment(Long meetingId, CommentRequest dto, Long memberId) {
        meetingService.getMeetingDtl(meetingId, memberId); // 존재 검증

        Comment comment = Comment.builder()
                .targetId(meetingId)
                .targetType(TargetType.MEETING)
                .content(dto.getContent())
                .isDel(false)
                .build();

        Comment saved = commentRepository.save(comment);

        String writerName = memberService.getMemberNameById(saved.getCreatedBy());

        return CommentDto.fromComment(saved, writerName);
    }
}
