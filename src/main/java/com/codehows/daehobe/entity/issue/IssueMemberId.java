package com.codehows.daehobe.entity.issue;

import lombok.*;

import java.io.Serializable;
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
//Serializable: 객체를 바이트 스트림으로 변환
// PA 내부에서 PK 객체를 비교하거나 캐싱할 때 직렬화(serialization)가 필요
public class IssueMemberId implements Serializable {
    private Long memberId;
    private Long issueId;
}
