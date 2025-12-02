package com.codehows.daehobe.entity;

import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// JPA가 엔티티 생명주기 이벤트(예: 저장, 수정 등)를 감지할 때, AuditingEntityListener가 자동으로 호출되어 생성일/수정일을 자동으로 세팅
@EntityListeners(AuditingEntityListener.class) // auditing 기능 적용
@MappedSuperclass // 다른 엔티티 클래스들이 상속받을 때, 이 클래스의 필드들을 자식 엔티티의 컬럼으로 포함시킴. 독립적으로 쿼리 대상이 되는 테이블은 아님
@Getter
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false) // 수정할 수 없다
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;
}
