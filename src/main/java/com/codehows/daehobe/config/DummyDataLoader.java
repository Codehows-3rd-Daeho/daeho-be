package com.codehows.daehobe.config;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.entity.masterData.*;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.masterData.*;
import com.codehows.daehobe.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DummyDataLoader implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final JobPositionRepository jobPositionRepository;
    private final CategoryRepository categoryRepository;
    private final AllowedExtensionRepository allowedExtensionRepository;
    private final MaxFileSizeRepository maxFileSizeRepository;

    @Override
    public void run(String... args) throws Exception {
        //카테고리
        if (categoryRepository.count() == 0) {
            categoryRepository.save(Category.builder().name("일반업무").build());
            categoryRepository.save(Category.builder().name("영업/고객").build());
            categoryRepository.save(Category.builder().name("연구개발").build());
            categoryRepository.save(Category.builder().name("생산").build());
            categoryRepository.save(Category.builder().name("품질").build());

            System.out.println("✔ Category 더미 데이터 생성 완료");
        }

        // ================= 부서 =================
        if (departmentRepository.count() == 0) {
            departmentRepository.save(Department.builder().name("경영").build());
            departmentRepository.save(Department.builder().name("경영지원실").build());
            departmentRepository.save(Department.builder().name("경영비서실").build());
            departmentRepository.save(Department.builder().name("기획실").build());
            departmentRepository.save(Department.builder().name("부설연구소").build());
            departmentRepository.save(Department.builder().name("영업마케팅").build());
            departmentRepository.save(Department.builder().name("자재구매팀").build());
            departmentRepository.save(Department.builder().name("생산팀").build());
            departmentRepository.save(Department.builder().name("품질경영팀").build());
            departmentRepository.save(Department.builder().name("생산개발팀").build());

            System.out.println("✔ Department 더미 데이터 생성 완료");
        }

        // ================= 직급 =================
        if (jobPositionRepository.count() == 0) {
            jobPositionRepository.save(JobPosition.builder().name("대표이사").build());
            jobPositionRepository.save(JobPosition.builder().name("이사").build());
            jobPositionRepository.save(JobPosition.builder().name("실장").build());
            jobPositionRepository.save(JobPosition.builder().name("부장").build());
            jobPositionRepository.save(JobPosition.builder().name("팀장").build());
            jobPositionRepository.save(JobPosition.builder().name("차장").build());
            jobPositionRepository.save(JobPosition.builder().name("과장").build());
            jobPositionRepository.save(JobPosition.builder().name("대리").build());
            jobPositionRepository.save(JobPosition.builder().name("주임").build());
            jobPositionRepository.save(JobPosition.builder().name("사원").build());
            jobPositionRepository.save(JobPosition.builder().name("수석연구원").build());
            jobPositionRepository.save(JobPosition.builder().name("책임연구원").build());
            jobPositionRepository.save(JobPosition.builder().name("선임연구원").build());
            jobPositionRepository.save(JobPosition.builder().name("연구원").build());
            jobPositionRepository.save(JobPosition.builder().name("프로").build());
            jobPositionRepository.save(JobPosition.builder().name("프로(P/L)").build());
            jobPositionRepository.save(JobPosition.builder().name("담당").build());

            System.out.println("✔ JobPosition 더미 데이터 생성 완료");
        }

        // ================= 허용 확장자 =================
        if (allowedExtensionRepository.count() == 0) {
            List<String> extensions = List.of(
                    "png",
                    "jpg",
                    "jpeg",
                    "gif",
                    "pdf",
                    "xls",
                    "xlsx",
                    "doc",
                    "docx",
                    "ppt",
                    "pptx",
                    "txt",
                    "zip",
                    "exe"
            );

            for (String ext : extensions) {
                allowedExtensionRepository.save(
                        AllowedExtension.builder()
                                .name(ext)
                                .build()
                );
            }

            System.out.println("✔ AllowedExtension 더미 데이터 생성 완료");
        }
        // ================= 최대 파일 사이즈 =================
        if (maxFileSizeRepository.count() == 0) {
            maxFileSizeRepository.save(
                    new MaxFileSize(
                            1L,
                            10L * 1024 * 1024         // 10MB
                    )
            );

            System.out.println("✔ MaxFileSize 더미 데이터 생성 완료 (10MB)");
        }
    }


}
