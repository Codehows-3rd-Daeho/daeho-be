package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.dto.issue.FilterDto;
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
            LocalDateTime end);

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


    // 리스트 조회 (필터, 검색)
    @Query("SELECT DISTINCT m FROM Meeting m " +
            "LEFT JOIN m.category c " +
            "LEFT JOIN m.meetingMembers mm " +
            "LEFT JOIN mm.member mem " +
            "LEFT JOIN MeetingDepartment mdpt ON mdpt.meeting = m " +
            "LEFT JOIN mdpt.department d " +
            "LEFT JOIN m.meetingMembers hostMm ON hostMm.isHost = true " +
            "LEFT JOIN hostMm.member hostM " +
            "WHERE m.isDel = false " +

            //  키워드 검색 (부서명 포함)
            "AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (" +
            "   m.title LIKE %:#{#filter.keyword}% OR " +
            "   c.name LIKE %:#{#filter.keyword}% OR " +
            "   mem.name LIKE %:#{#filter.keyword}% OR " +
            "   d.name LIKE %:#{#filter.keyword}% " +
            ")) " +

            // 부서 ID 리스트 필터링
            "AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds}) " +

            //  필터들
            "AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds}) " +
            "AND (:#{#filter.hostIds} IS NULL OR (hostMm.isHost = true AND hostM.id IN :#{#filter.hostIds})) " +
            "AND (:#{#filter.participantIds} IS NULL OR (mm.isHost = false AND mem.id IN :#{#filter.participantIds})) " +
            "AND (:#{#filter.statuses} IS NULL OR m.status IN :#{#filter.statuses}) " +
            "AND (:memberId IS NULL OR mm.member.id = :memberId) " +
            "AND (:#{#filter.startDate} IS NULL OR m.startDate >= :#{#filter.startDate == null ? null : #filter.startDate.atStartOfDay()}) " +
            "AND (:#{#filter.endDate} IS NULL OR m.startDate <= :#{#filter.endDate == null ? null : #filter.endDate.atTime(23, 59, 59)})")
    Page<Meeting> findMeetingsWithFilter(@Param("filter") FilterDto filter,
                                         @Param("memberId") Long memberId,
                                         Pageable pageable);

}
