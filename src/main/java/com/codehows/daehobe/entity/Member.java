package com.codehows.daehobe.entity;

import com.codehows.daehobe.constant.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "member")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member extends BaseEntity {
    @Id
    @Column(name = "member_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 로그인ID
    @Column(unique = true, nullable = false)
    private String loginId;

    // 비밀번호
    @Column(nullable = false)
    private String password;

    // 이름
    @Column(nullable = false)
    private String name;

    // 부서
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // 직급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    // 전화번호
    @Column(nullable = false)
    private String phone;

    // 이메일
    @Column(nullable = false)
    private String email;

    // 프로필 이미지 url
    private String profileUrl;

    // 프로필 이미지 파일명
    private String profileFilename;

    // 재직여부
    @Column(nullable = false)
    private Boolean isEmployed;

    // 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
//
//    public static Member toEntity(MemberDto dto, PasswordEncoder encoder) {
//        return Member.builder()
//                .loginId(dto.)
//                .role(Role.USER) // 기본 권한 설정
//                .build();
//    }
}
