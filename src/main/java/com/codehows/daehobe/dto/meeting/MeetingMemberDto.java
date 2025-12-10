package com.codehows.daehobe.dto.meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMemberDto {
    private Long memberId;
    private String memberName;
    private String departmentName;
    @JsonProperty("isHost")
    private boolean host;
    @JsonProperty("isPermitted")
    private boolean permitted;
    @JsonProperty("isRead")
    private boolean read;

    public static MeetingMemberDto fromEntity(MeetingMember meetingMember) {
        return new MeetingMemberDto(
                meetingMember.getMemberId().getId(),
                meetingMember.getMemberId().getName()+" "+meetingMember.getMemberId().getJobPosition().getName(),
                meetingMember.getMemberId().getDepartment().getName(),
                meetingMember.isHost(),
                meetingMember.isPermitted(),
                meetingMember.isRead()
        );
    }
}
