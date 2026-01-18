package com.codehows.daehobe.masterData.repository;

import com.codehows.daehobe.masterData.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department,Long> {
    List<Department> findByIdIn(List<Long> id);

    boolean existsByName(String departmentName);

    Optional<Department> findByName(String departmentName);
}
