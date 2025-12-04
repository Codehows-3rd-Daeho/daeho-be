package com.codehows.daehobe.dto.masterData;

import com.codehows.daehobe.entity.member.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PartMemberDto {
    private Long id;
    private String name;
    private String jobPositionName;


    // Entity → DTO 변환
    public static PartMemberDto fromEntity(Member member) {
        return new PartMemberDto(
                member.getId(),
                member.getName(),
                member.getJobPosition().getName() // Lazy라도 트랜잭션 내 safe
        );
    }

}
