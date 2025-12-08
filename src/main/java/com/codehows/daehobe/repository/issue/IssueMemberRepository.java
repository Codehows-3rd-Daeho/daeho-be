package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueMemberRepository extends JpaRepository<IssueMember,Long> {
    IssueMember findByIssueIdAndIsHost(Issue issue, boolean b);
}
