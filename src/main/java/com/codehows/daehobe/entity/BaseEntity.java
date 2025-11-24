package com.codehows.daehobe.entity;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class) // auditing 기능 적용
@MappedSuperclass // 공통 매핑 정보만 제공
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false) // 수정할 수 없다
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    //@ManyToOne(fetch = FetchType.LAZY)
    private String createdBy;
    //private Member createdBy

    @LastModifiedBy
    //@ManyToOne(fetch = FetchType.LAZY)
    private String updatedBy;
    //private Member updatedBy
}
