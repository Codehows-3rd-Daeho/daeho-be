package com.codehows.daehobe.entity.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMemberId {
    private Long memberId;
    private Long meetingId;
}
