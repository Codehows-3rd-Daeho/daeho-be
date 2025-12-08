package com.codehows.daehobe.dto.meeting;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
//DTO를 통해 필요한 정보만 클라이언트로 전달
//엔티티 노출 최소화 → 보안 및 성능 측면 유리
//직관적으로 isHost 값을 바로 접근 가능(IssueDto에서 사용)
public class MeetingMemberDto {
    private Long memberId;
    private String memberName;
    private boolean isHost;
    private boolean isPermitted;
    private boolean isRead;
}
