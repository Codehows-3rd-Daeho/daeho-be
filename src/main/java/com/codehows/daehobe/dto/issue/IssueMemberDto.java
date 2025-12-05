package com.codehows.daehobe.dto.issue;

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
    private boolean isHost;
    private boolean isPermitted;
    private boolean isRead;
}
