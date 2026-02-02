package com.codehows.daehobe.notification.repository;

import com.codehows.daehobe.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n " +
            "LEFT JOIN FETCH n.createdByMember " +
            "WHERE n.member.id = :memberId",
            countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.member.id = :memberId")
    Page<Notification> findByMemberIdWithCreatedByMember(@Param("memberId") Long memberId, Pageable pageable);

    int countByMemberIdAndIsReadFalse(Long memberId);
}
