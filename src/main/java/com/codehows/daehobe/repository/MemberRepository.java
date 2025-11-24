package com.codehows.daehobe.repository;
import com.codehows.daehobe.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member,Long>{
    Optional<Member> findByLoginId(String currentUsername);

    boolean existsByLoginId(String loginId);
}
