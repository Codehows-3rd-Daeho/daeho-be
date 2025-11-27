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

    private String extension;

    // 바이트로 변환하여 저장.
    private Long size;
}
