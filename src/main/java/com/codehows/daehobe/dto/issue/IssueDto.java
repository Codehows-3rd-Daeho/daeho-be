package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDto {
    // 왼쪽
    private String title;
    private String content;
    private List<FileDto> fileList;
    // 오른쪽
    private String status;
    private String hostName; // 주관자 이름
    private String hostJPName; // 주관자 직급
    private String startDate;
    private String endDate;
    private String categoryName;
    private List<String> departmentName;

    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime updatedAt;

    @JsonProperty("isDel")
    private boolean del;

    @JsonProperty("isEditPermitted")
    private boolean editPermitted; // 요청자가 수정, 삭제 권한자인지

    private List<IssueMemberDto> participantList; // 참여자

    public static IssueDto fromEntity(
            Issue issue,
            IssueMember host,
            List<String> departmentNames,
            List<FileDto> fileList,
            boolean isEditPermitted,
            List<IssueMemberDto> participantList
    ) {

        String hostName = host != null ? host.getMember().getName() : null;
        String hostJPName = host != null && host.getMember().getJobPosition() != null
                ? host.getMember().getJobPosition().getName()
                : null;

        return IssueDto.builder()
                .title(issue.getTitle())
                .content(issue.getContent())
                .fileList(fileList)
                .status(issue.getStatus().toString())
                .hostName(hostName)
                .hostJPName(hostJPName)
                .startDate(issue.getStartDate().toString())
                .endDate(issue.getEndDate().toString())
                .categoryName(issue.getCategory().getName())
                .departmentName(departmentNames)
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .del(issue.isDel())
                .editPermitted(isEditPermitted)
                .participantList(participantList)
                .build();
    }

}
