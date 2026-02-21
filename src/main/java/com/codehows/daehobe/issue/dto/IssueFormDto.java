package com.codehows.daehobe.issue.dto;

import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueDepartment;
import com.codehows.daehobe.issue.entity.IssueMember;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueFormDto {
    private Long id; //회의 등록시 사용
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
    private Boolean isPrivate;

    public static IssueFormDto fromEntity(
            Issue issue,
            List<IssueDepartment> departments,
            List<IssueMember> members
    ) {
        return IssueFormDto.builder()
                .id(issue.getId())
                .title(issue.getTitle())
                .status(issue.getStatus().name())
                .categoryId(issue.getCategory().getId())
                .departmentIds(
                        departments.stream()
                                .map(d -> d.getDepartment().getId())
                                .toList()
                )
                .members(
                        members.stream()
                                .map(IssueMemberDto::fromEntity)
                                .toList()
                )
                .isDel(issue.isDel())
                .isPrivate(issue.isPrivate())
                .build();
    }

}

