package com.codehows.daehobe.repository.issue;

import com.codehows.daehobe.constant.Status;
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
// 칸반 검색 포함 ==================
    // 1. 칸반: 진행중 (검색 포함)
    @Query("""
        SELECT DISTINCT i FROM Issue i
        LEFT JOIN i.category c
        LEFT JOIN i.issueMembers im ON im.isHost = true
        LEFT JOIN im.member m
        WHERE i.status = 'IN_PROGRESS' 
          AND i.isDel = false 
          AND (:kw IS NULL OR :kw = '' OR (
              i.title LIKE %:kw% 
              OR c.name LIKE %:kw% 
              OR m.name LIKE %:kw%
          ))
        ORDER BY i.endDate ASC
    """)
    List<Issue> findInProgressWithKeyword(@Param("kw") String keyword);

    // 2. 칸반: 미결 (검색 포함)
    @Query("""
        SELECT DISTINCT i FROM Issue i
        LEFT JOIN i.category c
        LEFT JOIN i.issueMembers im ON im.isHost = true
        LEFT JOIN im.member m
        WHERE i.status = 'IN_PROGRESS' 
          AND i.endDate < CURRENT_DATE 
          AND i.isDel = false 
          AND (:kw IS NULL OR :kw = '' OR (
              i.title LIKE %:kw% 
              OR c.name LIKE %:kw% 
              OR m.name LIKE %:kw%
          ))
        ORDER BY i.endDate ASC
    """)
    List<Issue> findDelayedWithKeyword(@Param("kw") String keyword);

    // 3. 칸반: 완료 - 최근 7일 (검색 포함)
    @Query("""
        SELECT DISTINCT i FROM Issue i
        LEFT JOIN i.category c
        LEFT JOIN i.issueMembers im ON im.isHost = true
        LEFT JOIN im.member m
        WHERE i.status = 'COMPLETED' 
          AND i.endDate >= :setDate 
          AND i.isDel = false 
          AND (:kw IS NULL OR :kw = '' OR (
              i.title LIKE %:kw% 
              OR c.name LIKE %:kw% 
              OR m.name LIKE %:kw%
          ))
        ORDER BY i.endDate DESC
    """)
    List<Issue> findRecentCompletedWithKeyword(@Param("setDate") LocalDate setDate, @Param("kw") String keyword);

    @Query("SELECT DISTINCT i FROM Issue i " +
            "LEFT JOIN i.category c " +
            "LEFT JOIN i.issueMembers im ON im.isHost = true " +
            "LEFT JOIN im.member m " +
            "WHERE i.isDel = false AND (" +
            "   i.title LIKE %:kw% " +
            "   OR str(i.status) LIKE %:kw% " +
            "   OR c.name LIKE %:kw% " +
            "   OR m.name LIKE %:kw%" +
            ")")
    Page<Issue> searchByKeyword(@Param("kw") String keyword, Pageable pageable);
}
