package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue,Long> {
    Optional<Issue> findById(Long id);
    List<Issue> findByIsDel(boolean isDel);

    // 더미
    Optional<Issue> findByTitle(String 서버_성능_개선_작업);
}
