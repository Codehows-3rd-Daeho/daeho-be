package com.codehows.daehobe.meeting.repository;

import com.codehows.daehobe.issue.dto.FilterDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.meeting.entity.Meeting;
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

//    Page<Meeting> findByIssueAndIsDelFalse(Issue issue, Pageable pageable);

    @Query("""
    SELECT m FROM Meeting m
    WHERE m.issue = :issue
    AND m.isDel = false
    AND (
        m.isPrivate = false
        OR EXISTS (
            SELECT 1 FROM MeetingMember mm
            WHERE mm.meeting = m AND mm.member.id = :memberId
        )
    )
""")
    Page<Meeting> findByIssueAndMemberId(@Param("issue") Issue issue,
                                         @Param("memberId") Long memberId,
                                         Pageable pageable);

    List<Meeting> findByMeetingMembers_Member_IdAndStartDateBetweenAndIsDelFalse(
            Long memberId,
            LocalDateTime start,
            LocalDateTime end);

    // 전체 회의 일정표 조회 본인 미참여 비밀글 제외
    @Query("""
    SELECT DISTINCT m
    FROM Meeting m
    WHERE m.isDel = false
    AND m.startDate <= :end 
    AND (m.endDate IS NULL OR m.endDate >= :start)
    AND (
        (m.isPrivate = false OR m.isPrivate IS NULL)
        OR
        (:memberId IS NOT NULL AND EXISTS (
            SELECT 1 FROM MeetingMember mm 
            WHERE mm.meeting = m AND mm.member.id = :memberId
        ))
    )
""")
    List<Meeting> findCalendarMeetings(
            @Param("memberId") Long memberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
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


    // 리스트 조회 (필터, 검색)
    @Query("""
                SELECT DISTINCT m
                FROM Meeting m
                LEFT JOIN m.category c
                LEFT JOIN m.meetingMembers mm
                LEFT JOIN mm.member mem
                LEFT JOIN MeetingDepartment mdpt ON mdpt.meeting = m
                LEFT JOIN mdpt.department d
                LEFT JOIN m.meetingMembers hostMm ON hostMm.isHost = true
                LEFT JOIN hostMm.member hostM
                WHERE m.isDel = false
                 AND (
                     (:isMyWork = true AND :memberId IS NOT NULL AND EXISTS (
                         SELECT 1 FROM MeetingMember mm2
                         WHERE mm2.meeting = m AND mm2.member.id = :memberId
                     ))
                     OR
                     (:isMyWork = false AND (
                         (m.isPrivate = false OR m.isPrivate IS NULL)
                         OR
                         (:memberId IS NOT NULL AND EXISTS (
                             SELECT 1 FROM MeetingMember mm3
                             WHERE mm3.meeting = m AND mm3.member.id = :memberId
                         ))
                     ))
                 )
                AND (
                    :#{#filter.departmentIds} IS NULL 
                    OR d.id IN :#{#filter.departmentIds}
                )
                AND (
                    :#{#filter.categoryIds} IS NULL 
                    OR c.id IN :#{#filter.categoryIds}
                )
                AND (
                    :#{#filter.hostIds} IS NULL 
                    OR (hostMm.isHost = true AND hostM.id IN :#{#filter.hostIds})
                )
                AND (
                    :#{#filter.participantIds} IS NULL 
                    OR (mm.isHost = false AND mem.id IN :#{#filter.participantIds})
                )
                AND (
                    :#{#filter.statuses} IS NULL 
                    OR m.status IN :#{#filter.statuses}
                )
             AND (
                 (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NULL)
         
                 OR (:#{#filter.startDate} IS NOT NULL AND :#{#filter.endDate} IS NULL
                     AND (
                         (m.endDate IS NOT NULL AND m.endDate >= :#{#filter.startDate?.atStartOfDay()})
                         OR (m.endDate IS NULL AND m.startDate >= :#{#filter.startDate?.atStartOfDay()})
                     )
                 )
         
                 OR (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NOT NULL
                     AND m.startDate <= :#{#filter.endDate?.atTime(23, 59, 59)}
                 )
         
                 OR (m.startDate <= :#{#filter.endDate?.atTime(23, 59, 59)}
                     AND (
                         m.endDate IS NULL
                         OR m.endDate >= :#{#filter.startDate?.atStartOfDay()}
                     )
                 )
             )
            """)
    Page<Meeting> findMeetingsWithFilter(
            @Param("filter") FilterDto filter,
            @Param("memberId") Long memberId,
            @Param("startDt") LocalDateTime startDt,
            @Param("endDt") LocalDateTime endDt,
            @Param("isMyWork") boolean isMyWork,
            Pageable pageable
    );
}
