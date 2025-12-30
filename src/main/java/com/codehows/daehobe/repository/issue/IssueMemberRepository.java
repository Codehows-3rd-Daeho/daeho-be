package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssueMemberRepository extends JpaRepository<IssueMember,Long> {
    IssueMember findByIssueAndIsHost(Issue issue, boolean b);

    // 이슈의 모든 멤버
    List<IssueMember> findAllByIssue(Issue issue);

    Optional<IssueMember> findByIssueAndMember(Issue issue, Member member);

    List<IssueMember> findByIssue(Issue issue);

    void deleteByIssue(Issue issue);

    //나의 업무에서 사용
    //:memberId : 파라미터 바인딩
    @Query("""
                SELECT im FROM IssueMember im
                JOIN im.issue i
                WHERE im.member.id = :memberId
                  AND i.isDel = false
            """)
    Page<IssueMember> findByMemberId(@Param("memberId") Long memberId, Pageable pageable);


    Optional<IssueMember> findByIssueIdAndMemberId(Long id, Long memberId);
}
