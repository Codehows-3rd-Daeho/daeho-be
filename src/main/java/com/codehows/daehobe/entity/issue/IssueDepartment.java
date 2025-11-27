package com.codehows.daehobe.entity.issue;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.masterData.Department;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_dpt")
@IdClass(IssueDepartmentId.class) // 복합키: member_id + issue_id
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDepartment  extends BaseEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issueId;


    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department departmentId;



}
