package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_position")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPosition extends BaseEntity {

    @Id
    @Column(name = "job_position_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 직급명
    @Column(unique = true, nullable = false)
    private String name;

    public void changeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("직급 이름은 필수입니다.");
        }
        this.name = name;
    }
}
