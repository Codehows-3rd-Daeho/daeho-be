package com.codehows.daehobe.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_positon")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job_position extends BaseEntity {

    @Id
    @Column(name = "job_positon_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 직급명
    @Column(unique = true, nullable = false)
    private String name;

}
