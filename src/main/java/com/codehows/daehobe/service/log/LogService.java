package com.codehows.daehobe.service.log;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.log.LogDto;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.repository.log.LogRepository;
import com.codehows.daehobe.service.comment.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LogService {

    private final LogRepository logRepository;
    private final CommentService commentService;

    public Page<LogDto> getIssueLogs(Long issueId, Pageable pageable) {
        // 1. 부모 댓글 ID 조회
        List<Long> commentIds = commentService.getCommentsByIssueId(issueId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(CommentDto::getId)
                .toList();

        // 2. 부모 로그 + 댓글 로그 조회
        Page<Log> logs = logRepository.findAllByParentAndCommentIds(TargetType.ISSUE, issueId, commentIds, pageable);

        return logs.map(LogDto::fromEntity);
    }

    public Page<LogDto> getMeetingLogs(Long meetingId, Pageable pageable) {
        List<Long> commentIds = commentService.getCommentsByMeetingId(meetingId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(CommentDto::getId)
                .toList();

        Page<Log> logs = logRepository.findAllByParentAndCommentIds(TargetType.MEETING, meetingId, commentIds, pageable);

        return logs.map(LogDto::fromEntity);
    }

    public Page<LogDto> getAllLogs(TargetType targetType, Pageable pageable) {
        Page<Log> logs;
        if (targetType != null) {
            logs = logRepository.findByTargetType(targetType, pageable);
        } else {
            logs = logRepository.findAll(pageable);
        }
        return logs.map(LogDto::fromEntity);
    }
}
