package com.codehows.daehobe.repository.file;

import com.codehows.daehobe.entity.file.File;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FileRepository extends JpaRepository<File,Long> {
}
