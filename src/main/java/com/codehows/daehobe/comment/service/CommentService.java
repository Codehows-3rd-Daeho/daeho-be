package com.codehows.daehobe.comment.service;

import com.codehows.daehobe.logging.AOP.annotations.TrackChanges;
import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.comment.dto.CommentDto;
import com.codehows.daehobe.comment.dto.CommentMentionDto;
import com.codehows.daehobe.comment.dto.CommentRequest;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.comment.repository.CommentRepository;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.issue.service.IssueService;
import com.codehows.daehobe.masterData.service.SetNotificationService;
import com.codehows.daehobe.meeting.service.MeetingService;
import com.codehows.daehobe.member.service.MemberService;
import com.codehows.daehobe.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final NotificationService notificationService;
    private final SetNotificationService setNotificationService;

    // 이슈 ==========================================
    // 이슈 댓글 호출
    public Page<CommentDto> getCommentsByIssueId(Long issueId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(issueId, TargetType.ISSUE, pageable);
        return comments.map(this::toCommentDto);
    }

    // 이슈 댓글 작성
    @TrackChanges(type = ChangeType.CREATE, target = TargetType.COMMENT)
    public Comment createIssueComment(Long issueId, CommentRequest dto, Long memberId, List<MultipartFile> multipartFiles) {
        issueService.getIssueDtl(issueId, memberId);
        Member writer = memberService.getMemberById(memberId);

        Comment saved = createAndSaveComment(
                issueId,
                TargetType.ISSUE,
                dto.getContent(),
                writer);

        if (dto.getMentionedMemberIds() != null && !dto.getMentionedMemberIds().isEmpty()) {
            mentionService.saveMentions(saved, dto.getMentionedMemberIds());

            // 멘션 알림 발송
            SetNotificationDto settingdto = setNotificationService.getSetting(); // 알림 설정
            if (settingdto.isCommentMention()) {
                notificationService.notifyMembers(
                        dto.getMentionedMemberIds(),   // 멘션된 사람만
                        memberId,                      // 작성자
                        writer.getName() + "님이 당신을 멘션했습니다 \n" + saved.getContent(),
                        "/issue/" + issueId
                );
            }
        }

        if (multipartFiles != null) {
            fileService.uploadFiles(saved.getId(), multipartFiles, TargetType.COMMENT);
        }

        return saved;
    }

    // 회의 ==================================
    // 회의 댓글 호출
    public Page<CommentDto> getCommentsByMeetingId(Long meetingId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByTargetIdAndTargetTypeAndIsDelFalse(meetingId, TargetType.MEETING, pageable);
        return comments.map(this::toCommentDto);
    }

    // 회의 댓글 작성
    @TrackChanges(type = ChangeType.CREATE, target = TargetType.COMMENT)
    public Comment createMeetingComment(Long meetingId, CommentRequest dto, Long memberId, List<MultipartFile> multipartFiles) {
        meetingService.getMeetingDtl(meetingId, memberId);
        Member writer = memberService.getMemberById(memberId);

        Comment saved = createAndSaveComment(
                meetingId,
                TargetType.MEETING,
                dto.getContent(),
                writer);

        if (dto.getMentionedMemberIds() != null && !dto.getMentionedMemberIds().isEmpty()) {
            mentionService.saveMentions(saved, dto.getMentionedMemberIds());

            // 멘션 알림 발송
            SetNotificationDto settingdto = setNotificationService.getSetting(); // 알림 설정
            if (settingdto.isCommentMention()) {
                notificationService.notifyMembers(
                        dto.getMentionedMemberIds(),   // 멘션된 사람만
                        memberId,                      // 작성자
                        writer.getName() + "님이 당신을 멘션했습니다 \n" + saved.getContent(),
                        "/meeting/" + meetingId
                );
            }
        }

        if (multipartFiles != null) {
            fileService.uploadFiles(saved.getId(), multipartFiles, TargetType.COMMENT);
        }

        return saved;
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

        File profile = fileService.findFirstByTargetIdAndTargetType(comment.getMember().getId(), TargetType.MEMBER);
        FileDto profileDto = profile == null ? null : FileDto.fromEntity(profile);
        List<FileDto> fileList = fileService.getCommentFiles(comment.getId());
        List<CommentMentionDto> mentions =
                mentionService.getMentionsByComment(comment);

        return CommentDto.fromComment(comment, writerName, writerJPName, profileDto, fileList, writer.getId(), mentions);
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
    @TrackChanges(type = ChangeType.UPDATE, target = TargetType.COMMENT)
    public Comment updateComment(Long id, CommentRequest dto, List<MultipartFile> newFiles, List<Long> removeFileIds) {
        Comment comment = getCommentById(id);

        // 1. 내용 업데이트
        comment.update(dto);

        // 2. 멘션 처리
        if (dto.getMentionedMemberIds() != null) {
            // 2-1. 기존 멘션 조회
            List<Long> beforeMentionIds =
                    mentionService.getMentionedMemberIds(comment.getId());

            List<Long> afterMentionIds = dto.getMentionedMemberIds();

            // 2-2. 새로 추가된 멘션만 계산
            Set<Long> newMentionIds = new HashSet<>(afterMentionIds);
            newMentionIds.removeAll(beforeMentionIds);

            // 2-3. 멘션 DB 갱신 (전체 삭제 → 재생성)
            mentionService.updateMentions(comment, afterMentionIds);

            // 2-4. 멘션 알림 발송
            if (!newMentionIds.isEmpty()) {
                SetNotificationDto settingdto = setNotificationService.getSetting();
                if (settingdto.isCommentMention()) {
                    Member writer = comment.getMember();
                    String targetUrl =
                            comment.getTargetType() == TargetType.ISSUE
                                    ? "/issue/" + comment.getTargetId()
                                    : "/meeting/" + comment.getTargetId();

                    notificationService.notifyMembers(
                            List.copyOf(newMentionIds),           // 새로 추가된 멘션만
                            writer.getId(),                       // 작성자
                            writer.getName() + "님이 당신을 멘션했습니다 \n"
                                    + comment.getContent(),
                            targetUrl
                    );
                }
            }


        }

        // 3. 파일 수정
        if ((newFiles != null && !newFiles.isEmpty()) || (removeFileIds != null && !removeFileIds.isEmpty())) {
            fileService.updateFiles(id, newFiles, removeFileIds, TargetType.COMMENT);
        }

        return comment;
    }


    @TrackChanges(type = ChangeType.DELETE, target = TargetType.COMMENT)
    public Comment deleteComment(Long id) {
        Comment comment = getCommentById(id);
        comment.delete();
        return comment;
    }

    // 댓글 단일 조회 > id
    public Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow(() -> new EntityNotFoundException("댓글이 존재하지 않습니다."));
    }


}
