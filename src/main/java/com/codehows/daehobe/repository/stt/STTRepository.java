package com.codehows.daehobe.repository.stt;

import com.codehows.daehobe.entity.file.STT;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface STTRepository extends JpaRepository<STT,Long> {

    List<STT> findByMeetingId(Long meetingId);
}
