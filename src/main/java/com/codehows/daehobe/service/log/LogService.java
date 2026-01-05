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


    public Page<LogDto> getIssueLogs(Long issueId, Pageable pageable) {

        Page<Log> logs = logRepository.findAllLogsIncludingDeleted(TargetType.ISSUE, issueId, pageable);

        return logs.map(LogDto::fromEntity);
    }

    public Page<LogDto> getMeetingLogs(Long meetingId, Pageable pageable) {

        Page<Log> logs = logRepository.findAllLogsIncludingDeleted(TargetType.MEETING, meetingId, pageable);

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
