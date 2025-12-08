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
@IdClass(MeetingDepartmentId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingDepartment extends BaseEntity {

//        @Id
//        @Column(name = "meeting_id")
//        private Long meetingId;
//
//        @Id
//        @Column(name = "department_id")
//        private Long departmentId;
//
//        @ManyToOne(fetch = FetchType.LAZY)
//        @JoinColumn(name = "meeting_id", insertable = false, updatable = false)
//        private Meeting meeting;
//
//        @ManyToOne(fetch = FetchType.LAZY)
//        @JoinColumn(name = "department_id", insertable = false, updatable = false)
//        private Department department;

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "meeting_id", nullable = false)
        private Meeting meetingId;

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "department_id", nullable = false)
        private Department departmentId;
}

