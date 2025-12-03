package com.codehows.daehobe;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.masterData.CategoryRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.masterData.JobPositionRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DummyDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final MemberRepository memberRepository;
    private final JobPositionRepository jobPositionRepository;


    @Override
    @Transactional
    public void run(String... args) throws Exception {

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
