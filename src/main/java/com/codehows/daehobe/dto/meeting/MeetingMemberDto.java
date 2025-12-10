package com.codehows.daehobe.dto.meeting;

import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMemberDto {
    private Long id;
    private String name;
    private String jobPositionName;
    private String departmentName;
    @JsonProperty("isHost")
    private boolean host;
    @JsonProperty("isPermitted")
    private boolean permitted;
    @JsonProperty("isRead")
    private boolean read;

    public static MeetingMemberDto fromEntity(MeetingMember meetingMember) {
        return new MeetingMemberDto(
                meetingMember.getMember().getId(),
                meetingMember.getMember().getName(),
                meetingMember.getMember().getJobPosition().getName(),
                meetingMember.getMember().getDepartment().getName(),
                meetingMember.isHost(),
                meetingMember.isPermitted(),
                meetingMember.isRead()
        );
    }
}
