package com.codehows.daehobe.dto.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class MeetingDto {


    private String title;
    private String content;

    private String status;
    private String host;
    private Long issueId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;
    private LocalDate endDate;
    private Long categoryId;
    private List<Long> departmentIds;
    private List<MeetingMemberDto> members;
    private Boolean isDel;
}
