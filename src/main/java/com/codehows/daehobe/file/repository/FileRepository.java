package com.codehows.daehobe.file.repository;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File,Long> {
    List<File> findByTargetIdAndTargetType(Long id, TargetType targetType);

    Optional<File> findFirstByTargetIdAndTargetType(Long id, TargetType targetType);

    List<File> findByTargetIdInAndTargetType(List<Long> ids, TargetType targetType);
}
