package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


}
