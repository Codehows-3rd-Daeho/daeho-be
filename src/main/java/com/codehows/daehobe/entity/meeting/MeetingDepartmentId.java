package com.codehows.daehobe.entity.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingDepartmentId implements Serializable {
    private Long meetingId;
    private Long departmentId;
}
