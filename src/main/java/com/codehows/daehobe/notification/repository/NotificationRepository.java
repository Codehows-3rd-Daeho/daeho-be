package com.codehows.daehobe.notification.repository;

import com.codehows.daehobe.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);

    int countByMemberIdAndIsReadFalse(Long memberId);
}
