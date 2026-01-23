package com.codehows.daehobe.masterData.repository;

import com.codehows.daehobe.masterData.entity.AllowedExtension;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedExtensionRepository extends JpaRepository<AllowedExtension, Long> {
    boolean existsByName(String extensionName);
}
