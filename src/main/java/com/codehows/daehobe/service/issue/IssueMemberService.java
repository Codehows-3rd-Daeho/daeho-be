package com.codehows.daehobe.service.issue;

import com.codehows.daehobe.entity.Member;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.repository.MemberRepository;
import com.codehows.daehobe.repository.issue.IssueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueMemberService {

    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;


    public List<Member> saveIssueMember(Long issueId, List<Long> memberId){


        //1. 이슈 조회
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + issueId));
        //2. 참여자 조회
        List<Member> members = memberRepository.findByIdIn(memberId);
        //3. 이슈 참여자 엔티티 생성 및 저장
        List<IssueMember> issueMembers = members.stream()
                .map(member -> new IssueMember(issue, member))
                .toList();


        return null;
    }

}
