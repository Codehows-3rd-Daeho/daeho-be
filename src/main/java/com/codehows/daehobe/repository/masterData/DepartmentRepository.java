package com.codehows.daehobe.repository.masterData;

import com.codehows.daehobe.entity.masterData.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface DepartmentRepository extends JpaRepository<Department,Long> {
    List<Department> findByIdIn(List<Long> id);

    boolean existsByName(String departmentName);

    Optional<Department> findByName(String 부서1);
}
