package com.codehows.daehobe;

import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.repository.masterData.CategoryRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DummyDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;


    @Override
    public void run(String... args) throws Exception {

        if (categoryRepository.count() == 0) {
            categoryRepository.save(Category.builder().name("일반업무").build());
            categoryRepository.save(Category.builder().name("영업/고객").build());
            categoryRepository.save(Category.builder().name("연구개발").build());

            System.out.println("✔ Category 더미 데이터 생성 완료");
        }

        if (departmentRepository.count() == 0) {
            departmentRepository.save(Department.builder().name("기획").build());
            departmentRepository.save(Department.builder().name("디자인").build());
            departmentRepository.save(Department.builder().name("개발").build());

            System.out.println("✔ Department 더미 데이터 생성 완료");
        }
    }
}
