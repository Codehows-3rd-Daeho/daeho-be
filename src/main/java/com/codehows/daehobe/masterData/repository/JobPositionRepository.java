package com.codehows.daehobe.masterData.repository;

import com.codehows.daehobe.masterData.entity.JobPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition,Long> {
    boolean existsByName(String positionName);

    Optional<JobPosition> findByName(String 직급1);

}
