package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.meeting.MeetingMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
}
