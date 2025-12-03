package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.entity.issue.IssueMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartMemberRepository extends JpaRepository<IssueMember,Long> {
}
