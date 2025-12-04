package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.dto.issue.IssueMemberDto;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.repository.issue.IssueMemberRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueMemberService {

    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final IssueMemberRepository partMemberRepository;



    public List<IssueMember> saveIssueMember(Long issueId, List<IssueMemberDto> issueMemberDtos){


        System.out.println("==========================================");
        System.out.println("이슈 멤버 서비스 작동 확인");
        System.out.println("==========================================");
        //1. 이슈 조회 issueId
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + issueId));
        List<IssueMember> issueMembers = issueMemberDtos.stream()
                .map(dto -> IssueMember.builder()
                        .issueId(issue)
                        .memberId(memberRepository.findById(dto.getMemberId())
                                .orElseThrow(() -> new RuntimeException("Member not found")))
                        .isHost(dto.isHost())
                        .isPermitted(dto.isPermitted()) // 프론트에서 보낸 값
                        .isRead(false)                 // 초기값 false
                        .build()
                ).toList();

        partMemberRepository.saveAll(issueMembers);

        return issueMembers;
    }

}
