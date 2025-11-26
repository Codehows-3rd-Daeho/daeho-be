package com.codehows.daehobe.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_setting_id", nullable = false)
    private Long id;

    @Column( nullable = false)
    private String extension;

    @Column(nullable = false)
    private Long size;
}
