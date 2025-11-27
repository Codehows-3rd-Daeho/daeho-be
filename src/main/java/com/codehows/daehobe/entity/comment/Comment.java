package com.codehows.daehobe.entity.comment;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment extends BaseEntity {

    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 이슈/회의 id
    @Column(nullable = false)
    private Long targetId;

    // 타겟 타입
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    // 본문
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 삭제상태 기본 false
    private boolean isDel;

}
