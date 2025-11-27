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
    private Status status;
    private Category categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Long> departmentIds;
    private List<Long> memberIds;
    private boolean isDel = false;



    //Entity -> Dto
    public static IssueDto fromEntity(Issue issue) {

        return IssueDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .status(issue.getStatus())
//                .categoryIds(issue.getCategoryId())
                .startDate(issue.getStartDate())
                .endDate(issue.getEndDate())
//                .department(issue.getDepartment())
//                .members(issue.getMembers())
                .isDel(false)
                .build();
    }



}

















