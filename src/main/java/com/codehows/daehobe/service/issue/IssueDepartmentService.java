package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.repository.DepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueDepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IssueDepartmentService {

    private DepartmentRepository departmentRepository;
    private IssueRepository issueRepository;
    private IssueDepartmentRepository issueDepartmentRepository;

    public  List<IssueDepartment> saveDepartment(Long issueId, List<Long> departmentId) {


        //1. 이슈 조회
//        Issue issue = issueRepository.findById(issueId);은 Optional에 대한 예외 처리가 없음
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + issueId));

        //2. 부서 조회
        List<Department> departments = departmentRepository.findByIdIn(departmentId);


        // 3. 이슈 부서 엔티티 생성 및 저장
        List<IssueDepartment> newLinks = departments.stream()
                .map(department -> new IssueDepartment(issue, department)) // IssueDepartment 객체 생성
                .collect(Collectors.toList());

        issueDepartmentRepository.saveAll(newLinks);


        return newLinks;
    }
}
