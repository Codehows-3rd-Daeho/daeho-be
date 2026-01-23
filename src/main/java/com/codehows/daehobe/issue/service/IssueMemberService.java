package com.codehows.daehobe.issue.service;

import com.codehows.daehobe.issue.dto.IssueMemberDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueMember;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.issue.repository.IssueMemberRepository;
import com.codehows.daehobe.issue.repository.IssueRepository;
import com.codehows.daehobe.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueMemberService {

    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final IssueMemberRepository issueMemberRepository;

    public List<IssueMember> saveIssueMember(Long issueId, List<IssueMemberDto> issueMemberDtos) {

        //1. 이슈 조회 issueId
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 ID의 이슈를 찾을 수 없습니다: " + issueId));
        List<IssueMember> issueMembers = issueMemberDtos.stream()
                .map(dto -> IssueMember.builder()
                        .issue(issue)
                        .member(memberRepository.findById(dto.getId())
                                .orElseThrow(() -> new RuntimeException("Member not found")))
                        .isHost(dto.isHost())
                        .isPermitted(dto.isPermitted()) // 프론트에서 보낸 값
                        .isRead(false)                 // 초기값 false
                        .build()
                ).toList();

        issueMemberRepository.saveAll(issueMembers);

        return issueMembers;
    }

    // 이슈로 참여자 리스트 조회
    public List<IssueMember> getMembers(Issue issue) {
        return issueMemberRepository.findByIssue(issue);
    }

    // 이슈로 주관자 조회
    public IssueMember getHost(Issue issue) {
        return issueMemberRepository.findAllByIssue(issue).stream()
                .filter(IssueMember::isHost)
                .findFirst()
                .orElse(null);
    }

    // 이슈, 멤버로 참여자 엔티티 찾기
    public IssueMember getMember(Issue issue, Member member) {
        return issueMemberRepository.findByIssueAndMember(issue, member).orElseThrow(EntityNotFoundException::new);
    }

    // 이슈와 관련된 참여자 삭제
    public void deleteIssueMember(Issue issue) {
        issueMemberRepository.deleteByIssue(issue);
    }


//    ================================================나의 업무=================================================================

    //로그인 사용자 id로 해당 이슈의 참여자인지 확인
    public boolean isParticipant(Long memberId, Issue issue) {
        return getMembers(issue).stream() // issue의 모든 참여자 조회
                .anyMatch(im -> im.getMember().getId().equals(memberId));
    }

    public Page<IssueMember> findByMemberId(Long memberId, Pageable pageable) {
        return issueMemberRepository.findByMemberId(memberId, pageable);
    }

    public IssueMember findByIssueIdAndMemberId(Long id, Long memberId) {
        return issueMemberRepository.findByIssueIdAndMemberId(id, memberId)
                .orElseThrow(() -> new EntityNotFoundException("이슈" + id + "에 참여자" + memberId + "가 존재하지 않습니다."));

    }
}
