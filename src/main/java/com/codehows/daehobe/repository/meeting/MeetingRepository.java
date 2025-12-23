package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.meeting.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Page<Meeting> findByIsDelFalse(Pageable pageable);

    Page<Meeting> findByIssueAndIsDelFalse(Issue issue, Pageable pageable);

    List<Meeting> findByStartDateBetweenAndIsDelFalse(
            LocalDateTime start,
            LocalDateTime end
    );


}
