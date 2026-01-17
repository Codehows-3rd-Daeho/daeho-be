package com.codehows.daehobe.masterData.repository;

import com.codehows.daehobe.masterData.entity.CustomGroup;
import com.codehows.daehobe.masterData.entity.CustomGroupMember;
import com.codehows.daehobe.masterData.entity.CustomGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomGroupMemberRepository extends JpaRepository<CustomGroupMember, CustomGroupMemberId> {
    List<CustomGroupMember> findAllByCustomGroupId(Long id);

    void deleteByCustomGroup(CustomGroup group);
}
