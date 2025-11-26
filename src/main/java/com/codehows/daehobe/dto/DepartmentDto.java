package com.codehows.daehobe.dto;

import com.codehows.daehobe.entity.Department;
import lombok.*;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DepartmentDto {
    private Long id;
    private String name;

    public Department toEntity() {
        return Department.builder()
                .id(this.id)
                .name(this.name)
                .build();
    }


    public static DepartmentDto fromEntity(Department department) {
        return DepartmentDto.builder()
                .id(department.getId())
                .name(department.getName())
                .build();
    }

}
