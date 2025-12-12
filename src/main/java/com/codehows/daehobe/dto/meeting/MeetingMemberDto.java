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

    public static MeetingMemberDto fromEntity(MeetingMember entity) {
        return MeetingMemberDto.builder()
                .id(entity.getMember().getId())
                .name(entity.getMember().getName())
                .departmentName(
                        entity.getMember().getDepartment() == null
                                ? null
                                : entity.getMember().getDepartment().getName()
                )
                .jobPositionName(
                        entity.getMember().getJobPosition() == null
                                ? null
                                : entity.getMember().getJobPosition().getName()
                )
                .host(entity.isHost())
                .permitted(entity.isPermitted())
                .read(entity.isRead())
                .build();
    }
}
