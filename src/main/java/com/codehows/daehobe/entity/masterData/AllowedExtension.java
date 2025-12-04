package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "allowed_extension")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedExtension extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allowed_extension_id", nullable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}