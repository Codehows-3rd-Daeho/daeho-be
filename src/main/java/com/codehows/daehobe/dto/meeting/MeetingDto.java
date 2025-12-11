package com.codehows.daehobe.dto.meeting;

import com.codehows.daehobe.dto.file.FileDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private String host; // 이름, 직급 포함.
    private Long issueId; // 관련 이슈 id
    private String issueTitle; // 관련 이슈 제목
    private String startDate;
    private String endDate;
    private String categoryName;
    private List<String> departmentName;
    private FileDto meetingMinutes; // 회의록

    private String createdAt;
    private String updatedAt;
    @JsonProperty("isDel")
    private boolean isDel;

    @JsonProperty("isEditPermitted")
    private boolean isEditPermitted; // 요청자가 수정, 삭제 권한자인지

    private List<MeetingMemberDto> participantList; // 참여자
}
