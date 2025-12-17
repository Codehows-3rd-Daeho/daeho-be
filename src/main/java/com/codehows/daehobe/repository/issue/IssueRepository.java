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

public interface IssueRepository extends JpaRepository<Issue,Long> {

    Optional<Issue> findById(Long id);

    List<Issue> findAllByIsDelFalse();

    Optional<Issue> findByIdAndIsDelFalseAndStatus(Long id, Status status);

    Page<Issue> findByIsDelFalse(Pageable pageable);


    // 이슈 리스트 조회용
    // 진행중 우선 가져오고 이후는 id순서
    @Query("""
    SELECT i FROM Issue i
    WHERE i.isDel = false
    ORDER BY 
        CASE WHEN i.status = 'IN_PROGRESS' THEN 0 ELSE 1 END,
        i.id DESC
""")
    Page<Issue> findAllWithStatusSort(Pageable pageable);

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
}
