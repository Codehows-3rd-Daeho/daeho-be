package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MeetingDepartmentRepository extends JpaRepository<MeetingDepartment,Long> {
    List<MeetingDepartment> findByMeetingId(Meeting meeting);
}
