package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueMemberRepository extends JpaRepository<IssueMember,Long> {
    IssueMember findByIssueIdAndIsHost(Issue issue, boolean b);
    Optional<IssueMember> findByIssueIdAndMemberId(Issue issue, Member member);

    List<IssueMember> findByIssueId(Issue issue);

    void deleteByIssueId(Issue issue);
}
