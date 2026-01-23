package com.codehows.daehobe.issue.entity;

import com.codehows.daehobe.common.entity.BaseEntity;
import com.codehows.daehobe.masterData.entity.Department;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_dpt")
@IdClass(IssueDepartmentId.class) // 복합키: member_id + issue_id
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDepartment extends BaseEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
}
