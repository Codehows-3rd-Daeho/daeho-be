package com.codehows.daehobe.issue.repository;

import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueDepartmentRepository extends JpaRepository<IssueDepartment,Long> {
    List<IssueDepartment> findByIssue(Issue issue);

    void deleteByIssue(Issue issue);
}
