package com.codehows.daehobe.repository;

import com.codehows.daehobe.entity.File;
import com.codehows.daehobe.entity.issue.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File,Long> {
}
