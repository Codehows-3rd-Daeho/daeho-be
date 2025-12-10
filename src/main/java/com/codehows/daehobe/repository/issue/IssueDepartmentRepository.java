package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueDepartmentRepository extends JpaRepository<IssueDepartment,Long> {
    List<IssueDepartment> findByIssue(Issue issue);

    void deleteByIssue(Issue issue);
}
