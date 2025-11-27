package com.codehows.daehobe.repository;
import com.codehows.daehobe.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member,Long> {
    Optional<Member> findByLoginId(String currentUsername);

    boolean existsByLoginId(String loginId);

    List<Member> findByIdIn(List<Long> id);

    int countByJobPositionId(Long id);

    int countByDepartmentId(Long id);
}
