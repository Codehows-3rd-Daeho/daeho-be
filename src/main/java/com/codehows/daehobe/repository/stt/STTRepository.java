package com.codehows.daehobe.repository.stt;

import com.codehows.daehobe.entity.stt.STT;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface STTRepository extends JpaRepository<STT,Long> {

    List<STT> findByMeetingId(Long meetingId);
}
