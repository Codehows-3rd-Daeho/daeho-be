package com.codehows.daehobe.dto.issue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class IssueFilterDto {
    private String keyword;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private List<Long> departmentIds;
    private List<Long> categoryIds;
    private List<Long> hostIds;
    private List<Long> participantIds;
    private List<String> statuses;
}
