package com.codehows.daehobe.entity.notification;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long meetingId; // PK

    @Column(nullable = false)
    private boolean issueCreated;

    @Column(nullable = false)
    private boolean issueUpdated;

    @Column(nullable = false)
    private boolean issueStatus;

    @Column(nullable = false)
    private boolean meetingCreated;

    @Column(nullable = false)
    private boolean meetingUpdated;

    @Column(nullable = false)
    private boolean meetingStatus;

    @Column(nullable = false)
    private boolean commentCreated;

    @Column(nullable = false)
    private boolean commentUpdated;

    @Column(nullable = false)
    private boolean commentMention;

}
