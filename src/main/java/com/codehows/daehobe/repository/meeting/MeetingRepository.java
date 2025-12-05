package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.meeting.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
}
