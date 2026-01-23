package com.codehows.daehobe.meeting.repository;

import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.entity.MeetingMember;
import com.codehows.daehobe.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    MeetingMember findByMeetingAndIsHost(Meeting meeting, boolean b);

    List<MeetingMember> findByMeeting(Meeting meeting);

    Optional<MeetingMember> findByMeetingAndMember(Meeting meeting, Member member);

    List<MeetingMember> findAllByMeeting(Meeting meeting);

    void deleteByMeeting(Meeting meeting);

    //나의 업무 회의 조회
    @Query("""
                SELECT mm FROM MeetingMember mm
                JOIN mm.meeting m
                WHERE mm.member.id = :memberId
                  AND m.isDel = false
            """)
    Page<MeetingMember> findByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    @Query("SELECT mm FROM MeetingMember mm " +
            "WHERE mm.meeting.startDate BETWEEN :start AND :end " +
            "AND mm.member.id = :memberId " +
            "AND mm.meeting.isDel = false")
    List<MeetingMember> findMeetingsByMemberAndDate(@Param("memberId") Long memberId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);


}
