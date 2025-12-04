package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "max_file_size")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MaxFileSize extends  BaseEntity {

    @Id
    @Column(name = "max_file_size_id", nullable = false)
    private Long id;

    @Column(name = "size_byte", nullable = false)
    private Long sizeByte;

    public void updateSize(Long newSizeInBytes) {
        this.sizeByte = newSizeInBytes;
    }
}