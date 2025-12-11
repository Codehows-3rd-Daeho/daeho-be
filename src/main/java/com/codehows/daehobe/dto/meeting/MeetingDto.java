package com.codehows.daehobe.dto.meeting;

import com.codehows.daehobe.dto.file.FileDto;
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

    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy.MM.dd HH:mm")
    private LocalDateTime updatedAt;

    @JsonProperty("isDel")
    private boolean del;

    @JsonProperty("isEditPermitted")
    private boolean editPermitted; // 요청자가 수정, 삭제 권한자인지

    private List<MeetingMemberDto> participantList; // 참여자
}
