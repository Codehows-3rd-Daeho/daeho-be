package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.issue.IssueFilterDto;
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

    List<Issue> findAllByIsDelFalse();

    Optional<Issue> findByIdAndIsDelFalseAndStatus(Long id, Status status);

    Page<Issue> findByIsDelFalse(Pageable pageable);

    // 칸반 조회용

    // 진행중
    @Query("""
                SELECT i FROM Issue i
                WHERE i.status = 'IN_PROGRESS'
                    AND i.isDel = false
                ORDER BY i.endDate ASC
            """)
    List<Issue> findInProgress();

    // 미결
    @Query("""
                SELECT i FROM Issue i
                WHERE i.status = 'IN_PROGRESS'
                  AND i.endDate < CURRENT_DATE
                  AND i.isDel = false
                ORDER BY i.endDate ASC
            """)
    List<Issue> findDelayed();

    // 완료 (최근 7일)
    @Query("SELECT i FROM Issue i WHERE i.status = 'COMPLETED' AND i.endDate >= :setDate AND i.isDel = false ORDER BY i.endDate DESC")
    List<Issue> findRecentCompleted(@Param("setDate") LocalDate setDate);

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

    // 1. 칸반: 진행중
    @Query("""
                SELECT DISTINCT i FROM Issue i
                LEFT JOIN i.category c
                LEFT JOIN i.issueMembers im
                LEFT JOIN im.member m
                LEFT JOIN IssueDepartment idpt ON idpt.issue = i
                LEFT JOIN idpt.department d
                WHERE i.status = 'IN_PROGRESS'
                  AND i.isDel = false
                  /* 키워드 검색 (부서명 d.name 포함) */
                  AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (
                      i.title LIKE %:#{#filter.keyword}% OR c.name LIKE %:#{#filter.keyword}% 
                      OR m.name LIKE %:#{#filter.keyword}% OR d.name LIKE %:#{#filter.keyword}%
                  ))
                  /* 필터 조건들 */
                  AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds})
                  AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds})
                  AND (:#{#filter.hostIds} IS NULL OR (im.isHost = true AND m.id IN :#{#filter.hostIds}))
                  AND (:#{#filter.participantIds} IS NULL OR (im.isHost = false AND m.id IN :#{#filter.participantIds}))
                AND (:#{#filter.startDate} IS NULL OR i.startDate >= CAST(:#{#filter.startDate} AS timestamp))
                AND (:#{#filter.endDate} IS NULL OR i.endDate <= CAST(:#{#filter.endDate} AS timestamp))
                ORDER BY i.endDate ASC
            """)
    List<Issue> findInProgressWithFilter(@Param("filter") IssueFilterDto filter);

    // 2. 칸반: 미결 (진행중이면서 종료일이 오늘보다 이전인 경우)
    @Query("""
                SELECT DISTINCT i FROM Issue i
                LEFT JOIN i.category c
                LEFT JOIN i.issueMembers im
                LEFT JOIN im.member m
                LEFT JOIN IssueDepartment idpt ON idpt.issue = i
                LEFT JOIN idpt.department d
                WHERE i.status = 'IN_PROGRESS'
                  AND i.endDate < CURRENT_DATE
                  AND i.isDel = false
                  AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (
                      i.title LIKE %:#{#filter.keyword}% OR c.name LIKE %:#{#filter.keyword}% 
                      OR m.name LIKE %:#{#filter.keyword}% OR d.name LIKE %:#{#filter.keyword}%
                  ))
                  AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds})
                  AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds})
                  AND (:#{#filter.hostIds} IS NULL OR (im.isHost = true AND m.id IN :#{#filter.hostIds}))
                  AND (:#{#filter.participantIds} IS NULL OR (im.isHost = false AND m.id IN :#{#filter.participantIds}))
                AND (:#{#filter.startDate} IS NULL OR i.startDate >= CAST(:#{#filter.startDate} AS timestamp))
                AND (:#{#filter.endDate} IS NULL OR i.endDate <= CAST(:#{#filter.endDate} AS timestamp))
                ORDER BY i.endDate ASC
            """)
    List<Issue> findDelayedWithFilter(@Param("filter") IssueFilterDto filter);

    // 3. 칸반: 완료 (상태가 COMPLETED이고 설정한 날짜(최근 7일) 이후 종료된 경우)
    @Query("""
                SELECT DISTINCT i FROM Issue i
                LEFT JOIN i.category c
                LEFT JOIN i.issueMembers im
                LEFT JOIN im.member m
                LEFT JOIN IssueDepartment idpt ON idpt.issue = i
                LEFT JOIN idpt.department d
                WHERE i.status = 'COMPLETED'
                  AND i.endDate >= :setDate
                  AND i.isDel = false
                  AND (:#{#filter.keyword} IS NULL OR :#{#filter.keyword} = '' OR (
                      i.title LIKE %:#{#filter.keyword}% OR c.name LIKE %:#{#filter.keyword}% 
                      OR m.name LIKE %:#{#filter.keyword}% OR d.name LIKE %:#{#filter.keyword}%
                  ))
                  AND (:#{#filter.categoryIds} IS NULL OR c.id IN :#{#filter.categoryIds})
                  AND (:#{#filter.departmentIds} IS NULL OR d.id IN :#{#filter.departmentIds})
                  AND (:#{#filter.hostIds} IS NULL OR (im.isHost = true AND m.id IN :#{#filter.hostIds}))
                  AND (:#{#filter.participantIds} IS NULL OR (im.isHost = false AND m.id IN :#{#filter.participantIds}))
                AND (:#{#filter.startDate} IS NULL OR i.startDate >= CAST(:#{#filter.startDate} AS timestamp))
                AND (:#{#filter.endDate} IS NULL OR i.endDate <= CAST(:#{#filter.endDate} AS timestamp))
                ORDER BY i.endDate DESC
            """)
    List<Issue> findRecentCompletedWithFilter(@Param("setDate") LocalDate setDate, @Param("filter") IssueFilterDto filter);

    @Query("""
            SELECT DISTINCT i
            FROM Issue i
            LEFT JOIN i.category c
            LEFT JOIN i.issueMembers im
            LEFT JOIN im.member m
            LEFT JOIN IssueDepartment idpt ON idpt.issue = i
            LEFT JOIN idpt.department d
            WHERE i.isDel = false
            
            /* keyword */
            AND (:#{#filter.keyword} IS NULL
                OR :#{#filter.keyword} = ''
                OR (
                    i.title LIKE %:#{#filter.keyword}%
                    OR c.name LIKE %:#{#filter.keyword}%
                    OR m.name LIKE %:#{#filter.keyword}%
                    OR d.name LIKE %:#{#filter.keyword}%
                ) )
            /* status */
            AND (
                :#{#filter.statuses} IS NULL
                OR i.status IN :#{#filter.statuses}
            )
            /* category */
            AND (
                :#{#filter.categoryIds} IS NULL
                OR c.id IN :#{#filter.categoryIds}
            )
            /* department */
            AND (
                :#{#filter.departmentIds} IS NULL
                OR d.id IN :#{#filter.departmentIds}
            )
            /* host */
            AND (
                :#{#filter.hostIds} IS NULL
                OR (
                    im.isHost = true
                    AND m.id IN :#{#filter.hostIds}
                )
            )
            /* participant */
            AND (:#{#filter.participantIds} IS NULL
                OR (
                    im.isHost = false
                    AND m.id IN :#{#filter.participantIds}
                ) )
            /* 기간 */
            AND (
                :#{#filter.startDate} IS NULL
                OR i.startDate >= CAST(:#{#filter.startDate} AS timestamp)
            )
            AND (
                :#{#filter.endDate} IS NULL
                OR i.endDate <= CAST(:#{#filter.endDate} AS timestamp)
            )
            """)
    Page<Issue> search(@Param("filter") IssueFilterDto filter, Pageable pageable);

    @Query("""
     SELECT DISTINCT i
               FROM Issue i
               LEFT JOIN i.category c
               LEFT JOIN i.issueMembers im
               LEFT JOIN im.member m
               LEFT JOIN IssueDepartment idpt ON idpt.issue = i
               LEFT JOIN idpt.department d
               WHERE i.isDel = false AND im.member.id = :#{#memberId}
               /* keyword */
               AND (:#{#filter.keyword} IS NULL
                   OR :#{#filter.keyword} = ''
                   OR (
                       i.title LIKE %:#{#filter.keyword}%
                       OR c.name LIKE %:#{#filter.keyword}%
                       OR m.name LIKE %:#{#filter.keyword}%
                       OR d.name LIKE %:#{#filter.keyword}%
                   ) )
               /* status */
               AND (
                   :#{#filter.statuses} IS NULL
                   OR i.status IN :#{#filter.statuses}
               )
               /* category */
               AND (
                   :#{#filter.categoryIds} IS NULL
                   OR c.id IN :#{#filter.categoryIds}
               )
               /* department */
               AND (
                   :#{#filter.departmentIds} IS NULL
                   OR d.id IN :#{#filter.departmentIds}
               )
               /* host */
               AND (
                   :#{#filter.hostIds} IS NULL
                   OR (
                       im.isHost = true
                       AND m.id IN :#{#filter.hostIds}
                   )
               )
               /* participant */
               AND (:#{#filter.participantIds} IS NULL
                   OR (
                       im.isHost = false
                       AND m.id IN :#{#filter.participantIds}
                   ) )
               /* 기간 */
               AND (
                   :#{#filter.startDate} IS NULL
                   OR i.startDate >= CAST(:#{#filter.startDate} AS timestamp)
               )
               AND (
                   :#{#filter.endDate} IS NULL
                   OR i.endDate <= CAST(:#{#filter.endDate} AS timestamp)
               )""")
    Page<Issue> findMyIssuesWithFilter(@Param("memberId") Long memberId, @Param("filter") IssueFilterDto filter, Pageable pageable);



    @Query("""
        SELECT DISTINCT i
        FROM Issue i
        LEFT JOIN i.category c
        LEFT JOIN i.issueMembers im
        LEFT JOIN im.member m
        LEFT JOIN IssueDepartment idpt ON idpt.issue = i
        LEFT JOIN idpt.department d
        WHERE i.isDel = false
          AND (:status IS NULL OR i.status = :status)
          AND (:keyword IS NULL OR :keyword = '' OR (
                i.title LIKE %:keyword% OR 
                c.name LIKE %:keyword% OR 
                m.name LIKE %:keyword% OR 
                d.name LIKE %:keyword%
          ))
          AND (:categoryIds IS NULL OR c.id IN :categoryIds)
          AND (:departmentIds IS NULL OR d.id IN :departmentIds)
          AND (:hostIds IS NULL OR (im.isHost = true AND m.id IN :hostIds))
          AND (:participantIds IS NULL OR (im.isHost = false AND m.id IN :participantIds))
          AND (:startDate IS NULL OR i.startDate >= :startDate)
          AND (:endDate IS NULL OR i.endDate <= :endDate)
          AND (:memberId IS NULL OR im.member.id = :memberId)
        ORDER BY i.endDate ASC
    """)
    Page<Issue> findIssuesWithFilter(
            @Param("status") Status status,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("departmentIds") List<Long> departmentIds,
            @Param("hostIds") List<Long> hostIds,
            @Param("participantIds") List<Long> participantIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("memberId") Long memberId,
            Pageable pageable
    );
}
