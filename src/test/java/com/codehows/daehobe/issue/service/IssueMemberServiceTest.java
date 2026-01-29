package com.codehows.daehobe.issue.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.issue.dto.IssueMemberDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueMember;
import com.codehows.daehobe.issue.repository.IssueMemberRepository;
import com.codehows.daehobe.issue.repository.IssueRepository;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class IssueMemberServiceTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private IssueMemberRepository issueMemberRepository;

    @InjectMocks
    private IssueMemberService issueMemberService;

    @Test
    @DisplayName("성공: 이슈 참여자 저장")
    void saveIssueMember_Success() {
        // given
        Long issueId = 1L;
        Issue issue = Issue.builder().id(issueId).build();
        Member member = Member.builder().id(1L).build();
        IssueMemberDto memberDto = IssueMemberDto.builder().id(1L).host(true).permitted(true).build();

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        // when
        issueMemberService.saveIssueMember(issueId, Collections.singletonList(memberDto));

        // then
        verify(issueMemberRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("실패: 이슈 참여자 저장 (이슈 없음)")
    void saveIssueMember_IssueNotFound() {
        // given
        Long issueId = 99L;
        IssueMemberDto memberDto = IssueMemberDto.builder().id(1L).build();
        when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> issueMemberService.saveIssueMember(issueId, Collections.singletonList(memberDto)));
    }
    
    @Test
    @DisplayName("성공: 이슈로 참여자 목록 조회")
    void getMembers_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();
        IssueMember issueMember = IssueMember.builder().issue(issue).build();
        when(issueMemberRepository.findByIssue(issue)).thenReturn(Collections.singletonList(issueMember));
        
        // when
        List<IssueMember> result = issueMemberService.getMembers(issue);
        
        // then
        assertThat(result).hasSize(1);
    }
    
    @Test
    @DisplayName("성공: 이슈로 주관자 조회")
    void getHost_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();
        IssueMember host = IssueMember.builder().issue(issue).isHost(true).build();
        IssueMember participant = IssueMember.builder().issue(issue).isHost(false).build();
        when(issueMemberRepository.findAllByIssue(issue)).thenReturn(Arrays.asList(host, participant));
        
        // when
        IssueMember result = issueMemberService.getHost(issue);
        
        // then
        assertThat(result.isHost()).isTrue();
    }
    
    @Test
    @DisplayName("성공: 이슈와 멤버로 참여자 엔티티 조회")
    void getMember_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();
        Member member = Member.builder().id(1L).build();
        IssueMember issueMember = IssueMember.builder().issue(issue).member(member).build();
        when(issueMemberRepository.findByIssueAndMember(issue, member)).thenReturn(Optional.of(issueMember));
        
        // when
        IssueMember result = issueMemberService.getMember(issue, member);
        
        // then
        assertThat(result).isEqualTo(issueMember);
    }

    @Test
    @DisplayName("성공: 이슈 참여자 삭제")
    void deleteIssueMember_Success() {
        // given
        Issue issue = Issue.builder().id(1L).build();

        // when
        issueMemberService.deleteIssueMember(issue);
        
        // then
        verify(issueMemberRepository).deleteByIssue(issue);
    }

    @Test
    @DisplayName("성공: 멤버 ID로 참여 이슈 페이지 조회")
    void findByMemberId_Success() {
        // given
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<IssueMember> page = new PageImpl<>(Collections.singletonList(new IssueMember()));
        when(issueMemberRepository.findByMemberId(memberId, pageable)).thenReturn(page);
        
        // when
        Page<IssueMember> result = issueMemberService.findByMemberId(memberId, pageable);
        
        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: 이슈 ID와 멤버 ID로 참여자 조회")
    void findByIssueIdAndMemberId_Success() {
        // given
        Long issueId = 1L;
        Long memberId = 1L;
        IssueMember issueMember = new IssueMember();
        when(issueMemberRepository.findByIssueIdAndMemberId(issueId, memberId)).thenReturn(Optional.of(issueMember));
        
        // when
        IssueMember result = issueMemberService.findByIssueIdAndMemberId(issueId, memberId);
        
        // then
        assertThat(result).isEqualTo(issueMember);
    }

    @Test
    @DisplayName("실패: 이슈 ID와 멤버 ID로 참여자 조회 (없음)")
    void findByIssueIdAndMemberId_NotFound() {
        // given
        Long issueId = 1L;
        Long memberId = 1L;
        when(issueMemberRepository.findByIssueIdAndMemberId(issueId, memberId)).thenReturn(Optional.empty());
        
        // when & then
        assertThrows(EntityNotFoundException.class, () -> issueMemberService.findByIssueIdAndMemberId(issueId, memberId));
    }
}
