package com.codehows.daehobe.controller.log;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.issue.IssueListDto;
import com.codehows.daehobe.dto.log.LogDto;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.repository.log.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogController {
    private final LogRepository logRepository;

    @GetMapping("/issue/{id}/log")
    public ResponseEntity<?> issueLog(@PathVariable Long id,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Log> logs = logRepository.findByTargetIdAndTargetType(id, TargetType.ISSUE, pageable);
            Page<LogDto> dtoList = logs.map(LogDto::fromEntity);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 로그 조회 중 오류 발생");
        }
    }


}
