package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Department;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IssueListDto {

    private Long id;                     // PK
    private String title;                // 제목
    private String status;               // 상태

    private LocalDate startDate;         // 시작일
    private LocalDate endDate;           // 종료일

    private String categoryName;         //  카테고리명
    private List<String> departmentName; //  참여 부서명 리스트
    private String hostName;             //  주관자 이름
    private String hostJPName;          // 주관자 직급

    @JsonProperty("isDel")
    private boolean del;               // 삭제 여부
    @JsonProperty("isPrivate")
    private boolean isPrivate;


    // 주관자 부서명 빠진버전
    public static IssueListDto fromEntity(Issue issue, List<String> departmentName, String hostName, String hostJPName) {

        return IssueListDto.builder()
                .id(issue.getId())
                .title(issue.getTitle())
                .status(issue.getStatus().name())
                .startDate(issue.getStartDate())
                .endDate(issue.getEndDate())
                .categoryName(issue.getCategory().getName())
                .del(issue.isDel())
                .isPrivate(issue.isPrivate())
                .departmentName(departmentName)
                .hostName(hostName)
                .hostJPName(hostJPName)
                .build();
    }

}