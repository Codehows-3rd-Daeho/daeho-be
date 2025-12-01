package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "department")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseEntity {
    @Id
    @Column(name = "department_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부서명
    @Column(unique = true)
    private String name;
}
