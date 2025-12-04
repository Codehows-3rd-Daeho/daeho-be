package com.codehows.daehobe.config;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.masterData.CategoryRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.masterData.JobPositionRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DummyDataLoader implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final DepartmentRepository departmentRepository;
    private final JobPositionRepository jobPositionRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 인코딩
    private final CategoryRepository categoryRepository;


    @Override
    public void run(String... args) throws Exception {

        // 부서 생성 (없으면 생성)
        Department defaultDept = departmentRepository.findByName("부서1")
                .orElseGet(() -> departmentRepository.save(
                        Department.builder().name("부서1").build()
                ));

        // 직급 생성 (없으면 생성)
        JobPosition defaultPos = jobPositionRepository.findByName("직급1")
                .orElseGet(() -> jobPositionRepository.save(
                        JobPosition.builder().name("직급1").build()
                ));

        // 20명 더미 회원 생성 (없으면 생성)
        for (long i = 1; i <= 20; i++) {
            final String loginId = "user" + i;

            if (memberRepository.findByLoginId(loginId).isEmpty()) {
                Member member = Member.builder()
                        .loginId(loginId)
                        .password(passwordEncoder.encode("1234"))
                        .name("회원 " + i)
                        .department(defaultDept)
                        .jobPosition(defaultPos)
                        .phone("010-0000-" + String.format("%04d", i))
                        .email("user" + i + "@example.com")
                        .isEmployed(i % 2 == 0)
                        .role(Role.USER)
                        .build();

                memberRepository.save(member);
            }
        }


        System.out.println("더미 회원 20명 저장 완료!");


        //카테고리
        if (categoryRepository.count() == 0) {
            categoryRepository.save(Category.builder().name("일반업무").build());
            categoryRepository.save(Category.builder().name("영업/고객").build());
            categoryRepository.save(Category.builder().name("연구개발").build());
            categoryRepository.save(Category.builder().name("개구리").build());
            categoryRepository.save(Category.builder().name("구리구리").build());
            categoryRepository.save(Category.builder().name("고리고리").build());

            System.out.println("✔ Category 더미 데이터 생성 완료");
        }

        //부서
        if (departmentRepository.count() == 0) {
            departmentRepository.save(Department.builder().name("기획").build());
            departmentRepository.save(Department.builder().name("디자인").build());
            departmentRepository.save(Department.builder().name("개발").build());
            departmentRepository.save(Department.builder().name("발발").build());
            departmentRepository.save(Department.builder().name("발발이").build());
            departmentRepository.save(Department.builder().name("바라바라").build());

            System.out.println("✔ Department 더미 데이터 생성 완료");
        }

        //직급
        if (jobPositionRepository.count() == 0) {

            jobPositionRepository.save(JobPosition.builder().name("과장").build());
            jobPositionRepository.save(JobPosition.builder().name("사원").build());

            System.out.println("✔ JobPosition 더미 데이터 생성 완료");
        }

    }
}
