package com.codehows.daehobe.repository.masterData;

import com.codehows.daehobe.entity.masterData.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DepartmentRepository extends JpaRepository<Department,Long> {
    List<Department> findByIdIn(List<Long> id);
}
