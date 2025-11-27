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
    private boolean issueCreated = false;

    @Column(nullable = false)
    private boolean issueUpdated = false;

    @Column(nullable = false)
    private boolean issueStatus = false;

    @Column(nullable = false)
    private boolean meetingCreated = false;

    @Column(nullable = false)
    private boolean meetingUpdated = false;

    @Column(nullable = false)
    private boolean meetingStatus = false;

    @Column(nullable = false)
    private boolean commentCreated = false;

    @Column(nullable = false)
    private boolean commentUpdated = false;

    @Column(nullable = false)
    private boolean commentMention = false;

}
