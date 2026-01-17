package com.codehows.daehobe.logging.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.logging.dto.LogDto;
import com.codehows.daehobe.logging.entity.Log;
import com.codehows.daehobe.logging.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
