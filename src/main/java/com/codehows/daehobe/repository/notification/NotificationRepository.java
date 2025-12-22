package com.codehows.daehobe.repository.notification;

import com.codehows.daehobe.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);
}
