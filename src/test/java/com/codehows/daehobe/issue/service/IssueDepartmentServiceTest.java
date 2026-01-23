package com.codehows.daehobe.issue.service;

import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueDepartment;
import com.codehows.daehobe.issue.repository.IssueDepartmentRepository;
import com.codehows.daehobe.issue.repository.IssueRepository;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueDepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private IssueRepository issueRepository;
    @Mock
    private IssueDepartmentRepository issueDepartmentRepository;

    @InjectMocks
    private IssueDepartmentService issueDepartmentService;

    @Test
    @DisplayName("성공: 이슈 부서 저장")
    void saveDepartment_Success() {
        // given
        Long issueId = 1L;
        List<Long> departmentIds = Arrays.asList(1L, 2L);
        Issue issue = Issue.builder().id(issueId).build();
        Department dept1 = Department.builder().id(1L).name("개발부").build();
        Department dept2 = Department.builder().id(2L).name("기획부").build();

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(departmentRepository.findByIdIn(departmentIds)).thenReturn(Arrays.asList(dept1, dept2));

        // when
        List<IssueDepartment> result = issueDepartmentService.saveDepartment(issueId, departmentIds);

        // then
        assertThat(result).hasSize(2);
        verify(issueDepartmentRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("실패: 이슈 부서 저장 (이슈 없음)")
    void saveDepartment_IssueNotFound() {
        // given
        Long issueId = 99L;
        List<Long> departmentIds = Arrays.asList(1L);
        when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> issueDepartmentService.saveDepartment(issueId, departmentIds));
    }

    @Test
    @DisplayName("성공: 이슈로 부서 이름 조회")
    void getDepartmentName_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();
        Department dept = Department.builder().id(1L).name("개발부").build();
        IssueDepartment issueDept = new IssueDepartment(issue, dept);
        when(issueDepartmentRepository.findByIssue(issue)).thenReturn(Collections.singletonList(issueDept));

        // when
        List<String> result = issueDepartmentService.getDepartmentName(issue);

        // then
        assertThat(result).hasSize(1).contains("개발부");
    }

    @Test
    @DisplayName("성공: 이슈로 부서 엔티티 조회")
    void getDepartMent_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();
        Department dept = Department.builder().id(1L).name("개발부").build();
        IssueDepartment issueDept = new IssueDepartment(issue, dept);
        when(issueDepartmentRepository.findByIssue(issue)).thenReturn(Collections.singletonList(issueDept));

        // when
        List<IssueDepartment> result = issueDepartmentService.getDepartMent(issue);

        // then
        assertThat(result).hasSize(1).contains(issueDept);
    }

    @Test
    @DisplayName("성공: 이슈 부서 삭제")
    void deleteIssueDepartment_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();

        // when
        issueDepartmentService.deleteIssueDepartment(issue);

        // then
        verify(issueDepartmentRepository).deleteByIssue(issue);
    }
}
