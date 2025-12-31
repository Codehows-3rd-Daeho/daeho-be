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
            
                /* 키워드 검색 (제목, 카테고리, 멤버, 부서) */
                AND (
                    :#{#filter.keyword} IS NULL 
                    OR :#{#filter.keyword} = '' 
                    OR (
                        m.title LIKE %:#{#filter.keyword}% 
                        OR c.name LIKE %:#{#filter.keyword}% 
                        OR mem.name LIKE %:#{#filter.keyword}% 
                        OR d.name LIKE %:#{#filter.keyword}%
                    )
                )
            
                /* 부서 필터 */
                AND (
                    :#{#filter.departmentIds} IS NULL 
                    OR d.id IN :#{#filter.departmentIds}
                )
            
                /* 카테고리 필터 */
                AND (
                    :#{#filter.categoryIds} IS NULL 
                    OR c.id IN :#{#filter.categoryIds}
                )
            
                /* 주최자 필터 */
                AND (
                    :#{#filter.hostIds} IS NULL 
                    OR (hostMm.isHost = true AND hostM.id IN :#{#filter.hostIds})
                )
            
                /* 참여자 필터 */
                AND (
                    :#{#filter.participantIds} IS NULL 
                    OR (mm.isHost = false AND mem.id IN :#{#filter.participantIds})
                )
            
                /* 상태 필터 */
                AND (
                    :#{#filter.statuses} IS NULL 
                    OR m.status IN :#{#filter.statuses}
                )
            
                /* 특정 멤버 기준 조회 (나의 회의 등) */
                AND (
                    :memberId IS NULL 
                    OR mm.member.id = :memberId
                )
                            
            /* 기간 필터  */
             AND (
                 /* 1) 시작일, 종료일 모두 없음 */
                 (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NULL)
         
                 /* 2) 시작일만 있는 경우: 회의 종료일이 시작일 이후이거나, 종료일이 없더라도 회의 시작일이 필터 시작일 이후인 경우 */
                 OR (:#{#filter.startDate} IS NOT NULL AND :#{#filter.endDate} IS NULL\s
                     AND (
                         (m.endDate IS NOT NULL AND m.endDate >= :#{#filter.startDate?.atStartOfDay()})
                         OR (m.endDate IS NULL AND m.startDate >= :#{#filter.startDate?.atStartOfDay()})
                     )
                 )
         
                 /* 3) 종료일만 있는 경우: 회의 시작일이 필터 종료일 이전인 경우 */
                 OR (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NOT NULL\s
                     AND m.startDate <= :#{#filter.endDate?.atTime(23, 59, 59)}
                 )
         
                 /* 4) 시작일 + 종료일 모두 있는 경우: 기간 겹침 */
                 OR (m.startDate <= :#{#filter.endDate?.atTime(23, 59, 59)}
                     AND (
                         m.endDate IS NULL\s
                         OR m.endDate >= :#{#filter.startDate?.atStartOfDay()}
                     )
                 )
             )
            """)
    Page<Meeting>
    findMeetingsWithFilter(
            @Param("filter") FilterDto filter,
            @Param("memberId") Long memberId,
            Pageable pageable
    );
}
