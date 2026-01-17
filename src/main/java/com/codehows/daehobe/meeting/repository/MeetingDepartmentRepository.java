package com.codehows.daehobe.meeting.repository;

import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.entity.MeetingDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingDepartmentRepository extends JpaRepository<MeetingDepartment,Long> {
    List<MeetingDepartment> findByMeeting(Meeting meeting);

    void deleteByMeeting(Meeting meeting);
}
