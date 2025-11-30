package com.codehows.daehobe.dto.member;

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
}
