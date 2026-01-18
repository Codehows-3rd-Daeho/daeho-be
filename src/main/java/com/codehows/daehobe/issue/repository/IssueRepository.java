package com.codehows.daehobe.issue.repository;

import com.codehows.daehobe.common.constant.Status;
import com.codehows.daehobe.issue.dto.FilterDto;
import com.codehows.daehobe.issue.entity.Issue;
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
      AND (:status IS NULL OR i.status = :status)
      AND (:#{#filter.statuses} IS NULL OR i.status IN :#{#filter.statuses})
      
      AND (:isDelayed = false OR (i.status = 'IN_PROGRESS' AND i.endDate < CURRENT_DATE))
      
      AND (:setDate IS NULL OR i.endDate >= :setDate)

      AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (
            i.title LIKE %:#{#filter.keyword}% OR c.name LIKE %:#{#filter.keyword}% 
            OR m.name LIKE %:#{#filter.keyword}% OR d.name LIKE %:#{#filter.keyword}%
      ))
      AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds})
      AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds})
      AND (:#{#filter.hostIds} IS NULL OR (im.isHost = true AND m.id IN :#{#filter.hostIds}))
      AND (:#{#filter.participantIds} IS NULL OR (im.isHost = false AND m.id IN :#{#filter.participantIds}))
      
     AND (
         (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NULL)
         OR (:#{#filter.startDate} IS NOT NULL AND :#{#filter.endDate} IS NULL AND i.endDate >= :#{#filter.startDate})
         OR (:#{#filter.startDate} IS NULL AND :#{#filter.endDate} IS NOT NULL AND i.startDate <= :#{#filter.endDate})
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
