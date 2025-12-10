package com.codehows.daehobe;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.issue.IssueRepository;
import com.codehows.daehobe.repository.masterData.CategoryRepository;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("dummyDataLoaderV2")
@RequiredArgsConstructor
public class DummyDataLoader implements CommandLineRunner {

    private final IssueRepository issueRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final MemberRepository memberRepository;

    @Override
    public void run(String... args) {

        // 🔥 이미 issue 가 존재하면 더미 생성 안 함
        if (issueRepository.count() > 0) return;

        // ----------------------------------------
        // 🔽 이미 DB에 존재하는 데이터 사용
        // ----------------------------------------

        Category category = categoryRepository.findById(1L)
                .orElse(null);

        Department dept1 = departmentRepository.findById(1L)
                .orElse(null);

        Department dept2 = departmentRepository.findById(2L)
                .orElse(null);

        Member member1 = memberRepository.findById(1L)
                .orElse(null);

        Member member2 = memberRepository.findById(2L)
                .orElse(null);

        // ----------------------------------------
        // 🔥 더미 Issue 생성
        // ----------------------------------------
        Issue issue = Issue.builder()
                .title("더미 이슈 제목")
                .content("더미 이슈 내용입니다.")
                .status(Status.IN_PROGRESS)
                .categoryId(category)
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 9))
                .isDel(false)
                .build();

        issueRepository.save(issue);


        issueRepository.save(issue);

        System.out.println("🔥 더미 Issue 생성 완료");
    }
}
