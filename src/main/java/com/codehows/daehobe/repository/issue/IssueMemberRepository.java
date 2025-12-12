package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueMemberRepository extends JpaRepository<IssueMember,Long> {
    IssueMember findByIssueAndIsHost(Issue issue, boolean b);

    // 이슈의 모든 멤버
    List<IssueMember> findAllByIssue(Issue issue);

    Optional<IssueMember> findByIssueAndMember(Issue issue, Member member);

    List<IssueMember> findByIssue(Issue issue);

    void deleteByIssue(Issue issue);
}
