package com.codehows.daehobe.dto.member;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponseDto {
    private Long memberId;
    private String token;
    private String name;
    private String jobPosition;
    private String profileUrl;
    private String role;
}
