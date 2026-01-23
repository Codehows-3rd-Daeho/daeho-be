package com.codehows.daehobe.logging.entity;

import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Log extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long Id; // PK

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "update_field")
    private String updateField;

    // 사용자
    @Column(name = "member_name", nullable = false)
    private String memberName;

    @Column(name = "title")
    private String title;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "parent_type")
    private String parentType;
}
