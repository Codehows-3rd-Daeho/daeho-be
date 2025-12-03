package com.codehows.daehobe.repository.masterData;

import com.codehows.daehobe.entity.masterData.JobPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition,Long> {

    Optional<JobPosition> findByName(String name);

}
