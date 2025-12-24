package com.codehows.daehobe.entity.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "member")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member extends BaseEntity {
    @Id
    @Column(name = "member_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @JoinColumn(name = "department_id")
    private Department department;

    // 직급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id")
    private JobPosition jobPosition;

    // 전화번호
    @Column(nullable = false)
    private String phone;

    // 이메일
    @Column(nullable = false)
    private String email;

    // 재직여부
    @Column(nullable = false)
    private Boolean isEmployed;

    // 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public void update(MemberDto memberDto, Department dpt, JobPosition pos, PasswordEncoder passwordEncoder) {
        this.loginId = memberDto.getLoginId();
        this.name = memberDto.getName();
        this.department = dpt;
        this.jobPosition = pos;
        this.phone = memberDto.getPhone();
        this.email = memberDto.getEmail();
        this.isEmployed = memberDto.getIsEmployed();
        this.role = "ADMIN".equals(memberDto.getRole()) ? Role.ADMIN : Role.USER;

        if (memberDto.getPassword() != null && !memberDto.getPassword().isBlank()) {
            this.password = passwordEncoder.encode(memberDto.getPassword());
        }
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
