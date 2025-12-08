package com.codehows.daehobe.dto.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
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
    private LocalDate startDate;
    private LocalDate endDate;
    private Long categoryId;
    private List<Long> departmentIds;
    private List<MeetingMemberDto> members;
    private Boolean isDel;
}
