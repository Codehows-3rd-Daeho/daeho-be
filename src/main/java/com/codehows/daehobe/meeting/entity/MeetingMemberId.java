package com.codehows.daehobe.meeting.entity;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class MeetingMemberId {
    private Long member;
    private Long meeting;
}
