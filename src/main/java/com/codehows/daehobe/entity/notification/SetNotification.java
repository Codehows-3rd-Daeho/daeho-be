package com.codehows.daehobe.entity.notification;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "set_notification")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetNotification extends BaseEntity {
    @Id
    @Column(name = "set_notification_id", nullable = false)
    private Long id;

    @Column(nullable = false)
    private boolean issueCreated;

    @Column(nullable = false)
    private boolean issueStatus;

    @Column(nullable = false)
    private boolean meetingCreated;

    @Column(nullable = false)
    private boolean meetingStatus;

    @Column(nullable = false)
    private boolean commentMention;

}
