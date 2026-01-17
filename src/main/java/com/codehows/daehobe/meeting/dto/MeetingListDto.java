package com.codehows.daehobe.meeting.dto;

import com.codehows.daehobe.meeting.entity.Meeting;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MeetingListDto {

    private Long id;                     // PK
    private String title;                // 제목
    private String status;               // 상태

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;         // 시작일
    private LocalDateTime endDate;           // 종료일

    private String categoryName;         //  카테고리명
    private List<String> departmentName; //  참여 부서명 리스트
    private String hostName;             //  주관자 이름
    private String hostJPName;          // 주관자 직급

    @JsonProperty("isDel")
    private boolean del;               // 삭제 여부

    @JsonProperty("isPrivate")
    private boolean isPrivate;          // 비밀글 여부
    private String color;


    // 주관자 부서명 빠진버전
    public static MeetingListDto fromEntity(Meeting meeting, List<String> departmentName, String hostName, String hostJPName) {

        return MeetingListDto.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .status(meeting.getStatus().name())
                .startDate(meeting.getStartDate())
                .endDate(meeting.getEndDate())
                .categoryName(meeting.getCategory().getName())
                .del(meeting.isDel())
                .isPrivate(meeting.isPrivate())
                .color(meeting.getColor())
                .departmentName(departmentName)
                .hostName(hostName)
                .hostJPName(hostJPName)
                .build();
    }

}