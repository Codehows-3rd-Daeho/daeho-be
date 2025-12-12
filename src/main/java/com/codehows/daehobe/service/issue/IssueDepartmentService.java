package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueDepartmentRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final IssueRepository issueRepository;
    private final IssueDepartmentRepository issueDepartmentRepository;

    public  List<IssueDepartment> saveDepartment(Long issueId, List<Long> departmentIds) {


        //1. 이슈 조회
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + issueId));

        //2. 부서 조회
        List<Department> departments = departmentRepository.findByIdIn(departmentIds);


        // 3. 이슈 부서 엔티티 생성 및 저장
        List<IssueDepartment> issueDepartmentList = departments.stream()
                .map(department -> new IssueDepartment(issue, department)) // IssueDepartment 객체 생성
                .collect(Collectors.toList());

        issueDepartmentRepository.saveAll(issueDepartmentList);


        return issueDepartmentList;
    }

    // 이슈로 부서이름 찾기
    public List<String> getDepartmentName(Issue issue) {
        return issueDepartmentRepository.findByIssue(issue).stream()
                .map(d -> d.getDepartment().getName())
                .toList();
    }

    // 이슈로 부서 엔티티 찾기
    public List<IssueDepartment> getDepartMent(Issue issue){
        return issueDepartmentRepository.findByIssue(issue);
    }

    // 이슈 부서 삭제
    public void deleteIssueDepartment(Issue issue) {
        issueDepartmentRepository.deleteByIssue(issue);
    }
}
