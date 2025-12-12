package com.codehows.daehobe.repository.meeting;

import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    MeetingMember findByMeetingAndIsHost(Meeting meeting, boolean b);

    List<MeetingMember> findByMeeting(Meeting meeting);

    Optional<MeetingMember> findByMeetingAndMember(Meeting meeting, Member member);

    List<MeetingMember> findAllByMeeting(Meeting meeting);

    void deleteByMeeting(Meeting meeting);
}
