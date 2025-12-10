package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.entity.issue.IssueMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

// 이슈 참여자 등록 dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
//직관적으로 isHost 값을 바로 접근 가능(IssueDto에서 사용)
public class IssueMemberDto {
    private Long memberId;
    private String memberName;
    private String departmentName;
    @JsonProperty("isHost")
    private boolean host;
    @JsonProperty("isPermitted")
    private boolean permitted;
    @JsonProperty("isRead")
    private boolean read;

    //회의에서 이슈 조회시 사용
    public static IssueMemberDto fromEntity(IssueMember entity) {
        return IssueMemberDto.builder()
                .memberId(entity.getMemberId().getId())                 // Long
                .memberName(entity.getMemberId().getName())             // String
                .departmentName(entity.getMemberId().getDepartment().getName()) // String
                .isHost(entity.isHost())
                .isPermitted(entity.isPermitted())
                .isRead(entity.isRead())
                .build();
    }

}
