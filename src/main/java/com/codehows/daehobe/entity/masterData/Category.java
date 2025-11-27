package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "category")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends BaseEntity {
    @Id
    @Column(name = "category_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 카테고리명
    @Column(unique = true, nullable = false)
    private String name;
}
