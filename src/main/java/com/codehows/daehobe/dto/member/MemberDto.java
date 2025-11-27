package com.codehows.daehobe.dto.member;

import lombok.Getter;

@Getter
public class MemberDto {

    // 로그인ID
    private String loginId;

    // 비밀번호
    private String password;

    // 이름
    private String name;

    // 부서
    private Long departmentId;

    // 직급
    private Long jobPositionId;

    // 전화번호
    private String phone;

    // 이메일
    private String email;

    // 재직여부
    private Boolean isEmployed;

    // 프로필 이미지 url
    private String profileUrl;

    // 프로필 이미지 파일명
    private String profileFilename;
}
