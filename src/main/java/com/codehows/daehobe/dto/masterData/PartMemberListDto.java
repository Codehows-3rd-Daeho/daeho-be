package com.codehows.daehobe.dto.masterData;

import com.codehows.daehobe.entity.member.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 참여자 목록 조회
@Getter
@AllArgsConstructor
public class PartMemberListDto {
    private Long id;
    private String name;
    private String department;
    private String jobPositionName;


    // Entity → DTO 변환
    public static PartMemberListDto fromEntity(Member member) {
        return new PartMemberListDto(
                member.getId(),
                member.getName(),
                member.getDepartment().getName(),
                member.getJobPosition().getName() // Lazy라도 트랜잭션 내 safe
        );
    }

}
