package com.codehows.daehobe.masterData.entity;

import com.codehows.daehobe.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "custom_group")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomGroup extends BaseEntity {
    @Id
    @Column(name = "custom_group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카테고리명
    @Column(unique = true)
    private String name;

    public void updateName(String name) {
        this.name = name;
    }

}
