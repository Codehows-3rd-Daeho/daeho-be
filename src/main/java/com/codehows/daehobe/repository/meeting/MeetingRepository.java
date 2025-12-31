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

    // 회의 검색
    @Query("SELECT DISTINCT mt FROM Meeting mt " +
            "LEFT JOIN mt.category c " +
            "LEFT JOIN mt.meetingMembers mtm ON mtm.isHost = true " +
            "LEFT JOIN mtm.member m " +
            "WHERE mt.isDel = false AND (" +
            "   mt.title LIKE %:kw% " +
            "   OR str(mt.status) LIKE %:kw% " +
            "   OR c.name LIKE %:kw% " +
            "   OR m.name LIKE %:kw%" +
            ")")
    Page<Meeting> searchByKeyword(@Param("kw") String keyword, Pageable pageable);

    // 나의 회의 검색
    @Query("SELECT DISTINCT m FROM Meeting m " +
            "JOIN m.meetingMembers mm " +
            "LEFT JOIN m.category c " +
            "LEFT JOIN m.meetingMembers hostMm ON hostMm.isHost = true " +
            "LEFT JOIN hostMm.member hostM " +
            "WHERE mm.member.id = :memberId " +
            "AND m.isDel = false " +
            "AND (:kw IS NULL OR :kw = '' OR (" +
            "   m.title LIKE %:kw% " +
            "   OR c.name LIKE %:kw% " +
            "   OR hostM.name LIKE %:kw%" +
            "))")
    Page<Meeting> searchMyMeetings(@Param("memberId") Long memberId,
                                   @Param("kw") String keyword, Pageable pageable);
}
