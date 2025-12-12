package com.codehows.daehobe.dto.meeting;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class MeetingFormDto {
    private Long id;
    private String title;
    private String content;
    private String status;
    private String host;
    private Long issueId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endDate;
    private Long categoryId;
    private List<Long> departmentIds;
    private List<MeetingMemberDto> members;
    private Boolean  isDel;
}
