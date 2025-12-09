package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.entity.issue.Issue;
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

    private String category;         //  카테고리명
    private List<String> department; //  참여 부서명 리스트
    private String hostName;             //  주관자 이름

    @JsonProperty("isDel")
    private boolean isDel;               // 삭제 여부

    public static IssueListDto fromEntity(Issue issue) {

        // 부서명 리스트
        List<String> departmentNames = issue.getIssueDepartments().stream()
                .map(d -> d.getDepartmentId().getName()) // Department 엔티티의 name
                .toList();

        // 카테고리명
        String categoryName = issue.getCategoryId().getName();

        // host 찾기
        String hostName = issue.getIssueMembers().stream()
                .filter(m -> m.isHost())
                .findFirst()
                .map(m -> m.getMemberId().getName())
                .orElse(null);

        return IssueListDto.builder()
                .id(issue.getIssueId())
                .title(issue.getTitle())
                .status(issue.getStatus().name())
                .startDate(issue.getStartDate())
                .endDate(issue.getEndDate())
                .category(categoryName)
                .department(departmentNames)
                .hostName(hostName)
                .isDel(issue.isDel())
                .build();
    }

}
