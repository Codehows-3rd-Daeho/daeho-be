package com.codehows.daehobe.repository.masterData;

import com.codehows.daehobe.entity.masterData.AllowedExtension;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedExtensionRepository extends JpaRepository<AllowedExtension, Long> {
    boolean existsByName(String extensionName);
}
