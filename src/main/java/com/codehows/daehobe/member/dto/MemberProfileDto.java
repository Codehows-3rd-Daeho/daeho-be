package com.codehows.daehobe.member.dto;

import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileDto {
    private String loginId;
    private String name;
    private String email;
    private String phone;
    private String departmentName;
    private String jobPositionName;
    private String profileUrl;


    public static MemberProfileDto fromEntity(Member member, File profileFile){
        return MemberProfileDto.builder()
                .loginId(member.getLoginId())
                .name(member.getName())
                .email(member.getEmail())
                .phone(member.getPhone())
                .departmentName(member.getDepartment()  != null ? member.getDepartment().getName() : null)
                .jobPositionName(member.getJobPosition() != null ? member.getJobPosition().getName() : null)
                .profileUrl(profileFile != null ? profileFile.getPath() : null)
                .build();
    }
}
