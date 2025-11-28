package com.codehows.daehobe;


import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.issue.IssueDepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Optional;

@Configuration
public class DummyDataLoader {

    @Bean
    public CommandLineRunner loadDummyData(
            IssueRepository issueRepository,
            IssueDepartmentRepository issueDepartmentRepository,
            IssueMemberRepository issueMemberRepository,
            MemberRepository memberRepository,
            DepartmentRepository departmentRepository
    ) {
        return args -> {

            // 🔒 이미 생성된 더미(또는 아무 Issue)가 있으면 스킵
            if (issueRepository.count() > 0) {
                System.out.println("📌 DummyDataLoader: Issue already exists. Skip loading.");
                return;
            }

            // 🔍 특정 제목으로 체크하는 방식도 가능
            Optional<Issue> exist = issueRepository.findByTitle("서버 성능 개선 작업");
            if (exist.isPresent()) {
                System.out.println("📌 DummyDataLoader: Dummy issue already exists. Skip.");
                return;
            }

            System.out.println("📌 DummyDataLoader: Creating issue dummy...");

            // 🔹 Member ID = 2
            Member member = memberRepository.findById(2L)
                    .orElseThrow(() -> new RuntimeException("Member 2 not found"));

            // 🔹 Department ID = 1
            Department department = departmentRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Department 1 not found"));

            // 🔹 Issue 생성
            Issue issue = Issue.builder()
                    .title("서버 성능 개선 작업")
                    .content("서버 응답 속도 개선을 위한 최적화 작업 진행")
                    .status(Status.IN_PROGRESS)
                    .categoryId(1L,)
                    .startDate(LocalDate.of(2025, 11, 28))
                    .endDate(LocalDate.of(2025, 12, 5))
                    .isDel(false)
                    .build();

            issueRepository.save(issue);

            // 🔹 Issue - Department 매핑
            IssueDepartment issueDept = IssueDepartment.builder()
                    .issueId(issue)
                    .departmentId(department)
                    .build();

            issueDepartmentRepository.save(issueDept);

            // 🔹 Issue - Member 매핑
            IssueMember issueMember = IssueMember.builder()
                    .issueId(issue)
                    .memberId(member)
                    .build();

            issueMemberRepository.save(issueMember);

            System.out.println("🎉 DummyDataLoader: Issue dummy created successfully!");
        };
    }
}
