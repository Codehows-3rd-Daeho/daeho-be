package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.meeting.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Page<Meeting> findByIsDelFalse(Pageable pageable);

    Page<Meeting> findByIssue(Issue issue, Pageable pageable );
}
