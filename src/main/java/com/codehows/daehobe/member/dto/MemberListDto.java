package com.codehows.daehobe.member.dto;

import com.codehows.daehobe.common.constant.Role;
import com.codehows.daehobe.member.entity.Member;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberListDto {
    private Long id;
    private String name;
    private String departmentName;
    private String jobPositionName;
    private String phone;
    private String email;
    private Boolean isEmployed;
    private Boolean isAdmin;

    public static MemberListDto fromEntity(Member member) {
        boolean isAdmin = member.getRole().equals(Role.ADMIN);

        return MemberListDto.builder()
                .id(member.getId())
                .name(member.getName())
                .departmentName(member.getDepartment() != null ? member.getDepartment().getName() : null)
                .jobPositionName(member.getJobPosition() != null ? member.getJobPosition().getName() : null)
                .phone(member.getPhone())
                .email(member.getEmail())
                .isEmployed(member.getIsEmployed())
                .isAdmin(isAdmin)
                .build();
    }
}
