package com.codehows.daehobe.service.comment;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.commnet.CommentRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.issue.IssueService;
import com.codehows.daehobe.service.meeting.MeetingService;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final FileService fileService;

    // 이슈 ==========================================
    // 이슈 댓글 호출
    public  Page<CommentDto> getCommentsByIssueId(Long issueId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(issueId, TargetType.ISSUE, pageable);
        return comments.map(this::toCommentDto);
    }

    // 이슈 댓글 작성
    public CommentDto createIssueComment(Long issueId, CommentRequest dto, Long memberId, List<MultipartFile> multipartFiles) {
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

        if (multipartFiles != null) {
            fileService.uploadFiles(saved.getId(), multipartFiles, TargetType.COMMENT);
        }

        return toCommentDto(saved);
    }
    // =================================

    // 회의 ==================================
    // 회의 댓글 호출
    public  Page<CommentDto> getCommentsByMeetingId(Long meetingId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(meetingId, TargetType.MEETING, pageable);
        return comments.map(this::toCommentDto);
    }

    // 회의 댓글 작성
    public CommentDto createMeetingComment(Long meetingId, CommentRequest dto, Long memberId, List<MultipartFile> multipartFiles) {
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

        if (multipartFiles != null) {
            fileService.uploadFiles(saved.getId(), multipartFiles, TargetType.COMMENT);
        }

        return toCommentDto(saved);
    }

    // 수정



    // ==============================

    // =======================
    // 공통 로직

    // 조회
    private CommentDto toCommentDto(Comment comment) {
        Member writer = comment.getMember();
        String writerName = writer.getName();
        String writerJPName = writer.getJobPosition() != null
                ? writer.getJobPosition().getName()
                : null;

        List<FileDto> fileList = fileService.getCommentFiles(comment.getId());

        return CommentDto.fromComment(comment,writerName, writerJPName, fileList, writer.getId());
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

    // 수정
    public Comment updateComment(Long id,
                                  CommentRequest dto,
                                  List<MultipartFile> newFiles,
                                  List<Long> removeFileIds){
        Comment comment = getCommentById(id);

        // 내용 수정
        comment.update(dto);

        // 파일 수정
        if ((newFiles != null && !newFiles.isEmpty()) || (removeFileIds != null && !removeFileIds.isEmpty())) {
            fileService.updateFiles(id, newFiles, removeFileIds, TargetType.COMMENT);
        }

        return comment;

    }

    public void deleteComment(Long id) {
        Comment comment = getCommentById(id);
        comment.delete();
    }

    // 댓글 단일 조회 > id
    public Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow(() -> new EntityNotFoundException("댓글이 존재하지 않습니다."));
    }


}
