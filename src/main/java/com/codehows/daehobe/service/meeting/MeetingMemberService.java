package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.dto.meeting.MeetingMemberDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.meeting.MeetingMemberRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 회의로 참여자 리스트 조회
    public List<MeetingMember> getMembers(Meeting meeting) {
        return meetingMemberRepository.findByMeeting(meeting);
    }

    // 회의, 멤버로 참여자 엔티티 찾기
    public MeetingMember getMember(Meeting meeting, Member member){
        return meetingMemberRepository.findByMeetingAndMember(meeting, member).orElseThrow(EntityNotFoundException::new);
    }

    // 회의로 주관자 조회
    public MeetingMember getHost(Meeting meeting) {
        return meetingMemberRepository.findAllByMeeting(meeting).stream()
                .filter(MeetingMember::isHost)
                .findFirst()
                .orElse(null);
    }

    // 미팅 > 주관자 이름 찾기
    public String getHostName(Meeting meeting) {
        return meetingMemberRepository.findAllByMeeting(meeting).stream()
                .filter(MeetingMember::isHost)
                .findFirst()
                .map(h -> h.getMember().getName())
                .orElse(null);
    }

    // 회의와 관련된 참여자 삭제
    public void deleteMeetingMember(Meeting meeting) {
        meetingMemberRepository.deleteByMeeting(meeting);
    }

//    ================================================나의 업무=================================================================

public Page<MeetingMember> findByMemberId(Long memberId, Pageable pageable) {
        return meetingMemberRepository.findByMemberId(memberId, pageable);
}


}
