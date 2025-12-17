package com.codehows.daehobe.repository.member;
import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.dto.comment.MentionMemberDto;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member,Long> {
    Optional<Member> findByLoginId(String currentUsername);

    boolean existsByLoginId(String loginId);

    int countByJobPositionId(Long id);

    List<Member> findByRoleAndIsEmployedTrue(Role role);

    @Query(value = """
    SELECT
        m.member_id        AS id,
        m.name             AS name,
        jp.name            AS jobPositionName,
        d.name             AS departmentName
    FROM member m
    JOIN job_position jp ON m.job_position_id = jp.job_position_id
    JOIN department d    ON m.department_id = d.department_id
    WHERE m.name LIKE CONCAT('%', :keyword, '%')
      AND m.is_employed = true
    LIMIT 10
""", nativeQuery = true)
    List<MentionMemberDto> searchForMention(@Param("keyword") String keyword);
}
