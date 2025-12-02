package com.codehows.daehobe.repository.file;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.file.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File,Long> {
    List<File> findByTargetIdAndTargetType(Long id, TargetType targetType);

    Optional<File> findFirstByTargetIdAndTargetType(Long id, TargetType targetType);
}
