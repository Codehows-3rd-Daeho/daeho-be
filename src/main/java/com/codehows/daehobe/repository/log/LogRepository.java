package com.codehows.daehobe.repository.log;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.log.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

public interface LogRepository extends CrudRepository<Log, Long> {
    Page<Log> findByTargetIdAndTargetType(Long id, TargetType targetType, Pageable pageable);

    Page<Log> findAll(Pageable pageable);


}