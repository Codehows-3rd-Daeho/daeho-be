package com.codehows.daehobe.dto;

import com.codehows.daehobe.entity.Category;
import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.entity.File;
import com.codehows.daehobe.entity.Status;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class IssueDto {
    private String title;
    private String content;
    private Status status;
    private Category categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Long> departmentId;
    private List<Long> memberId;
    private boolean isDel = false;



    //Entity -> Dto
    public static IssueDto fromEntity(Issue issue) {

        return IssueDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .status(issue.getStatus())
//                .categoryId(issue.getCategoryId())
                .startDate(issue.getStartDate())
                .endDate(issue.getEndDate())
//                .department(issue.getDepartment())
//                .members(issue.getMembers())
                .isDel(false)
                .build();
    }



}

















