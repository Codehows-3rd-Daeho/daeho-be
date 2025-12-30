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
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Page<Meeting> findByIsDelFalse(Pageable pageable);

    Page<Meeting> findByIssueAndIsDelFalse(Issue issue, Pageable pageable);

    List<Meeting> findByStartDateBetweenAndIsDelFalse(
            LocalDateTime start,
            LocalDateTime end
    );

    // 기존 상세 조회
    @Query("""
            SELECT DISTINCT m
            FROM Meeting m
            JOIN FETCH m.category c
            LEFT JOIN FETCH m.meetingMembers mm
            LEFT JOIN FETCH mm.member mem
            LEFT JOIN FETCH mem.jobPosition jp
            WHERE m.id = :meetingId
            """)
    Optional<Meeting> findDetailById(@Param("meetingId") Long meetingId);

}
