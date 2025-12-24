package com.codehows.daehobe.repository.masterData;

import com.codehows.daehobe.entity.masterData.CustomGroup;
import com.codehows.daehobe.entity.masterData.CustomGroupMember;
import com.codehows.daehobe.entity.masterData.CustomGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomGroupMemberRepository extends JpaRepository<CustomGroupMember, CustomGroupMemberId> {
    List<CustomGroupMember> findAllByCustomGroupId(Long id);

    void deleteByCustomGroup(CustomGroup group);
}
