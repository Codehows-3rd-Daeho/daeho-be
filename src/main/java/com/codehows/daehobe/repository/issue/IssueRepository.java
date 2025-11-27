package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue,Long> {
    Optional<Issue> findById(Long id);
}
