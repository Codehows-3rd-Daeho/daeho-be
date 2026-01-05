package com.codehows.daehobe.repository.log;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.log.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogRepository extends CrudRepository<Log, Long> {
    Page<Log> findByTargetIdAndTargetType(Long id, TargetType targetType, Pageable pageable);

    Page<Log> findAll(Pageable pageable);


    Page<Log> findByTargetType(TargetType targetType, Pageable pageable);

    @Query("""
    select l from Log l
    where (l.targetType = :parentType and l.targetId = :parentId)
       or (l.targetType = com.codehows.daehobe.constant.TargetType.COMMENT 
           and l.targetId in (
               select c.id from Comment c 
               where c.targetId = :parentId 
               and c.targetType = :parentType
           ))
""")
    Page<Log> findAllLogsIncludingDeleted(@Param("parentType") TargetType parentType,
                                          @Param("parentId") Long parentId,
                                          Pageable pageable);
}