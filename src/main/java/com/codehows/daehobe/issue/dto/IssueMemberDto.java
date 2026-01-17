package com.codehows.daehobe.issue.dto;

import com.codehows.daehobe.issue.entity.IssueMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

// 이슈 참여자 등록 dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueMemberDto {
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

    // 회의에서 이슈 조회시 사용
    public static IssueMemberDto fromEntity(IssueMember entity) {
        return IssueMemberDto.builder()
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
