package com.codehows.daehobe.entity.file;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId; // PK

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "saved_name", nullable = false)
    private String savedName;

    @Column(name = "size", nullable = false)
    private Long size;

    // 여러 타겟(이슈, 회의, 댓글, STT, 회원) 중 하나와 연결
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    public void updateFileSize(Long size) {
        this.size = size;
    }
}