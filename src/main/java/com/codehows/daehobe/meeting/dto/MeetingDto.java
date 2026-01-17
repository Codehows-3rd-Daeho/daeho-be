package com.codehows.daehobe.meeting.dto;

import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.entity.MeetingMember;
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
public class MeetingDto {
    // 왼쪽
    private String title;
    private String content;
    private List<FileDto> fileList;

    // 오른쪽
    private String status;
    private String hostName; // 주관자 이름
    private String hostJPName; // 주관자 직급
    private Long issueId; // 관련 이슈 id
    private String issueTitle; // 관련 이슈 제목
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime startDate;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime endDate;
    private String categoryName;
    private List<String> departmentName;
    private FileDto meetingMinutes; // 회의록
    private String totalSummary;
    private String remarks;

    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime updatedAt;

    @JsonProperty("isDel")
    private boolean del;

    @JsonProperty("isPrivate")
    private boolean isPrivate;

    @JsonProperty("isEditPermitted")
    private boolean editPermitted; // 요청자가 수정, 삭제 권한자인지

    private List<MeetingMemberDto> participantList; // 참여자

    public static MeetingDto fromEntity(
            Meeting meeting,
            MeetingMember host,
            List<String> departmentNames,
            List<FileDto> fileList,
            FileDto meetingMinutes,
            boolean isEditPermitted,
            List<MeetingMemberDto> participantList,
            String totalSummary
    ) {
        // 관련 이슈 (삭제상태가 true면 null)
        Issue issue = meeting.getIssue();
        if (issue != null && issue.isDel()) {
            issue = null;
        }

        String hostName = host != null ? host.getMember().getName() : null;
        String hostJPName = (host != null && host.getMember().getJobPosition() != null)
                ? host.getMember().getJobPosition().getName()
                : null;

        return MeetingDto.builder()
                .title(meeting.getTitle())
                .content(meeting.getContent())
                .fileList(fileList)
                .status(meeting.getStatus().toString())
                .hostName(hostName)
                .hostJPName(hostJPName)
                .issueId(issue != null ? issue.getId() : null)
                .issueTitle(issue != null ? issue.getTitle() : null)
                .startDate(meeting.getStartDate())
                .endDate(meeting.getEndDate())
                .categoryName(meeting.getCategory().getName())
                .departmentName(departmentNames)
                .meetingMinutes(meetingMinutes)
                .remarks(meeting.getRemarks())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .del(meeting.isDel())
                .isPrivate(meeting.isPrivate())
                .editPermitted(isEditPermitted)
                .participantList(participantList)
                .totalSummary(totalSummary)
                .build();
    }

}
