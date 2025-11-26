package com.codehows.daehobe.repository;

import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface DepartmentRepository extends JpaRepository<Department,Long> {
    List<Department> findByIdIn(List<Long> id);
}
