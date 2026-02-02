package com.codehows.daehobe.stt.repository;

import com.codehows.daehobe.stt.entity.STT;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;


public interface STTRepository extends JpaRepository<STT,Long> {

    @Query("""
    SELECT s FROM STT s
    WHERE s.meeting.id = :meetingId
    AND (
        s.status != 'RECORDING'
        OR (s.status = 'RECORDING' AND s.createdBy = :memberId)
    )
    """)
    List<STT> findByMeetingIdWithStatusCondition(
            @Param("meetingId") Long meetingId,
            @Param("memberId") Long memberId
    );

    List<STT> findByStatus(STT.Status status);

    List<STT> findByStatusAndRetryCountLessThan(STT.Status status, int maxRetryCount);

    @Query("SELECT s.id FROM STT s WHERE s.status = :status")
    Set<Long> findIdsByStatus(@Param("status") STT.Status status);
}
