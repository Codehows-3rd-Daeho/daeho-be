package com.codehows.daehobe.entity.meeting;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.masterData.Department;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meeting_dpt")
@IdClass(MeetingDepartmentId.class) // 복합키: member_id + issue_id
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingDepartment extends BaseEntity {

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "meeting_id", nullable = false)
        private Meeting meeingId;


        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "department_id", nullable = false)
        private Department departmentId;


}
