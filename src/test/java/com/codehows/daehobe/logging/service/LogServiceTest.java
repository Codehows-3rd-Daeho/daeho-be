package com.codehows.daehobe.logging.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.logging.dto.LogDto;
import com.codehows.daehobe.logging.entity.Log;
import com.codehows.daehobe.logging.repository.LogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @InjectMocks
    private LogService logService;

    @Test
    @DisplayName("성공: 이슈 로그 조회")
    void getIssueLogs_Success() {
        // given
        Long issueId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Log log = Log.builder().message("이슈 로그").build();
        Page<Log> logPage = new PageImpl<>(Collections.singletonList(log), pageable, 1);

        when(logRepository.findAllLogsIncludingDeleted(TargetType.ISSUE, issueId, pageable)).thenReturn(logPage);

        // when
        Page<LogDto> result = logService.getIssueLogs(issueId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMessage()).isEqualTo("이슈 로그");
    }

    @Test
    @DisplayName("성공: 회의 로그 조회")
    void getMeetingLogs_Success() {
        // given
        Long meetingId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Log log = Log.builder().message("회의 로그").build();
        Page<Log> logPage = new PageImpl<>(Collections.singletonList(log), pageable, 1);

        when(logRepository.findAllLogsIncludingDeleted(TargetType.MEETING, meetingId, pageable)).thenReturn(logPage);

        // when
        Page<LogDto> result = logService.getMeetingLogs(meetingId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMessage()).isEqualTo("회의 로그");
    }

    @Test
    @DisplayName("성공: 모든 로그 조회 (타입 지정)")
    void getAllLogs_WithTargetType() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Log log = new Log();
        Page<Log> logPage = new PageImpl<>(Collections.singletonList(log), pageable, 1);

        when(logRepository.findByTargetType(TargetType.ISSUE, pageable)).thenReturn(logPage);

        // when
        Page<LogDto> result = logService.getAllLogs(TargetType.ISSUE, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(logRepository).findByTargetType(TargetType.ISSUE, pageable);
    }

    @Test
    @DisplayName("성공: 모든 로그 조회 (타입 미지정)")
    void getAllLogs_WithoutTargetType() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Log log = new Log();
        Page<Log> logPage = new PageImpl<>(Collections.singletonList(log), pageable, 1);

        when(logRepository.findAll(any(Pageable.class))).thenReturn(logPage);

        // when
        Page<LogDto> result = logService.getAllLogs(null, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(logRepository).findAll(pageable);
    }
}
