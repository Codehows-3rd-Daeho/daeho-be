package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.entity.issue.Issue;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class IssueDto {
    private String title;
    private String content;
    private String status;
    private String host;
    private Long categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Long> departmentIds;
    private List<IssueMemberDto> members;
    private Boolean isDel;
}

