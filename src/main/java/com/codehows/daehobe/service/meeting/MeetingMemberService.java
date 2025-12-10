package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.meeting.MeetingMemberRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MeetingMemberService {

    private final MemberRepository memberRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;

    public List<MeetingMember> saveMeetingMember(Long meetingId, List<MeetingMemberDto> meetingMemberDtos) {


        System.out.println("==========================================");
        System.out.println("회의 멤버 서비스 작동 확인");
        System.out.println("==========================================");
        //1. 회의 조회 issueId
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + meetingId));
        List<MeetingMember> meetingMembers = meetingMemberDtos.stream()
                        .map(dto -> MeetingMember.builder()
                                .meeting(meeting)
                                .member(memberRepository.findById(dto.getId())
                                        .orElseThrow(() -> new EntityNotFoundException("member not found")))
                                .isHost(dto.isHost())
                                .isPermitted(dto.isPermitted())
                                .isRead(false)
                                .build()
                        ).toList();


        meetingMemberRepository.saveAll(meetingMembers);

        return meetingMembers;
    }
}
