package com.codehows.daehobe.meeting.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class MeetingDepartmentId implements Serializable {
    private Long meeting;
    private Long department;
}
