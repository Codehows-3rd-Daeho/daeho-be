package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.issue.FilterDto;
import com.codehows.daehobe.entity.issue.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findById(Long id);

    Optional<Issue> findByIdAndIsDelFalseAndStatus(Long id, Status status);

    List<Issue> findAllByIsDelFalseAndStatus(Status status);

    // 이슈 상세 조회
    @Query("""
                SELECT DISTINCT i
                FROM Issue i
                JOIN FETCH i.category c
                LEFT JOIN FETCH i.issueMembers im
                LEFT JOIN FETCH im.member m
                LEFT JOIN FETCH m.jobPosition jp
                WHERE i.id = :issueId
            """)
    Optional<Issue> findDetailById(@Param("issueId") Long issueId);

    @Query("""
    SELECT DISTINCT i
    FROM Issue i
    LEFT JOIN i.category c
    LEFT JOIN i.issueMembers im
    LEFT JOIN im.member m
    LEFT JOIN IssueDepartment idpt ON idpt.issue = i
    LEFT JOIN idpt.department d
    WHERE i.isDel = false
      
    /* 비밀글 */
      AND (
            
             (:isMyWork = false AND (
             i.isPrivate = false
             OR i.isPrivate IS NULL
             OR (:memberId IS NOT NULL AND EXISTS (
                 SELECT 1 FROM IssueMember im3
                 WHERE im3.issue = i AND im3.member.id = :memberId
             ))
         ))
         OR
         
         (:isMyWork = true AND :memberId IS NOT NULL AND EXISTS (
             SELECT 1 FROM IssueMember im3
             WHERE im3.issue = i AND im3.member.id = :memberId
         ))
     )
      /* 상태 필터 (단일 상태 혹은 리스트 처리) */
      AND (:status IS NULL OR i.status = :status)
      AND (:#{#filter.statuses} IS NULL OR i.status IN :#{#filter.statuses})
      
      /* 미결(Delayed) 전용 조건: status가 PROGRESS이면서 기간이 지난 경우 */
      AND (:isDelayed = false OR (i.status = 'IN_PROGRESS' AND i.endDate < CURRENT_DATE))
      
      /* 완료(Recent) 전용 조건: 특정 날짜 이후 완료된 건 */
      AND (:setDate IS NULL OR i.endDate >= :setDate)

      /* 키워드 및 필터 조건 */
      AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (
            i.title LIKE %:#{#filter.keyword}% OR c.name LIKE %:#{#filter.keyword}% 
            OR m.name LIKE %:#{#filter.keyword}% OR d.name LIKE %:#{#filter.keyword}%
      ))
      AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds})
      AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds})
      AND (:#{#filter.hostIds} IS NULL OR (im.isHost = true AND m.id IN :#{#filter.hostIds}))
      AND (:#{#filter.participantIds} IS NULL OR (im.isHost = false AND m.id IN :#{#filter.participantIds}))
      
      /* 특정 멤버 업무 조회 시 사용 */
      /*AND (:memberId IS NULL OR EXISTS (SELECT 1 FROM IssueMember im2 WHERE im2.issue = i AND im2.member.id = :memberId))*/
      
      /* 기간 필터 */
     AND (
            /* 1) 시작일, 종료일 둘 다 없는 경우 → 기간 조건 없이 전체 조회 */
         (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NULL)
            /* 2) 시작일만 있는 경우
                → 검색 시작일 이후까지 진행되는 이슈만 조회 */
         OR (:#{#filter.startDate} IS NOT NULL AND :#{#filter.endDate} IS NULL AND i.endDate >= :#{#filter.startDate})
            /* 3) 종료일만 있는 경우
                → 검색 종료일 이전에 시작한 이슈만 조회 */
         OR (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NOT NULL AND i.startDate <= :#{#filter.endDate})
            /* 4) 시작일과 종료일 모두 있는 경우
                → 이슈 기간과 검색 기간이 하루라도 겹치면 포함 */
         OR (i.startDate <= :#{#filter.endDate} AND i.endDate >= :#{#filter.startDate})
     )
     
    ORDER BY 
        CASE WHEN :status = 'COMPLETED' THEN i.endDate END DESC,
        CASE WHEN :status != 'COMPLETED' OR :status IS NULL THEN i.endDate END ASC
""")
    Page<Issue> findIssuesWithFilter(
            @Param("filter") FilterDto filter,
            @Param("status") Status status,
            @Param("isDelayed") boolean isDelayed,
            @Param("setDate") LocalDate setDate,
            @Param("memberId") Long memberId,
            @Param("isMyWork") boolean isMyWork,
            Pageable pageable
    );
}
