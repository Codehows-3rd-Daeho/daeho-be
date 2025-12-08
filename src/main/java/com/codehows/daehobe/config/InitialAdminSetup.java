package com.codehows.daehobe.config;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.masterData.JobPositionRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

// --- 4. 초기 데이터 설정 Configuration ---
@Configuration
@RequiredArgsConstructor
public class InitialAdminSetup {

    private final PasswordEncoder passwordEncoder;

    private final MemberRepository memberRepository;

    private final DepartmentRepository departmentRepository;

    private final JobPositionRepository jobPositionRepository;


    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {
            final String ADMIN_LOGIN_ID = "admin";
            final String ADMIN_PASSWORD = "12341234";

            final String YZOO_LOGIN_ID = "yzoo";



            // 1. 관리자 계정 존재 여부 확인
            if (memberRepository.findByLoginId(ADMIN_LOGIN_ID).isEmpty()) {



                // 3. Member 엔티티 생성 및 비밀번호 인코딩
                String encodedPassword = passwordEncoder.encode(ADMIN_PASSWORD);

                memberRepository.save(Member.builder()
                        .loginId(ADMIN_LOGIN_ID)
                        .password(encodedPassword)
                        .name("관리자")
//                        .department(defaultDept)
//                        .jobPosition(defaultPos)
                        .phone("010-0000-0000")
                        .email("admin@example.com")
                        .isEmployed(true)
                        .role(Role.ADMIN)
                        .build());

                memberRepository.save(Member.builder()
                        .loginId(YZOO_LOGIN_ID)
                        .password(encodedPassword)
                        .name("윤예주")
//                        .department(1)
//                        .jobPosition(1)  // 객체 연결
                        .phone("010-1111-1111")
                        .email("yyy@example.com")
                        .isEmployed(true)
                        .role(Role.USER)
                        .build());

                System.out.println("--- 초기 관리자 계정 생성 완료 (ID: " + ADMIN_LOGIN_ID + ", PW: " + ADMIN_PASSWORD + ") ---");
            } else {
                System.out.println("초기 관리자 계정(" + ADMIN_LOGIN_ID + ")이 이미 존재합니다. 생성 스킵.");
            }
        };
    }
}