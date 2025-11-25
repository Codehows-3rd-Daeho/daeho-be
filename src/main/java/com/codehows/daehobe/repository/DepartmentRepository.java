package com.codehows.daehobe.repository;

import com.codehows.daehobe.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department,Long> {
}
