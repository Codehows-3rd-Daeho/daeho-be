package com.codehows.daehobe.comment.service;

import com.codehows.daehobe.comment.dto.CommentMentionDto;
import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.comment.entity.Mention;
import com.codehows.daehobe.comment.repository.MentionRepository;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat; // argThat import
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock private MentionRepository mentionRepository;
    @Mock private MemberService memberService;

    @InjectMocks
    private MentionService mentionService;

    private Comment testComment;
    private Member writerMember;
    private Member mentionedMember1;
    private Member mentionedMember2;

    @BeforeEach
    void setUp() {
        writerMember = Member.builder().id(1L).name("작성자").build();
        mentionedMember1 = Member.builder().id(2L).name("멘션1").build();
        mentionedMember2 = Member.builder().id(3L).name("멘션2").build();
        testComment = Comment.builder().id(1L).member(writerMember).content("댓글 내용").build();
    }

    @Test
    @DisplayName("성공: 멘션 저장 (작성자 제외)")
    void saveMentions_Success_ExcludingWriter() {
        // given
        List<Long> mentionedMemberIds = Arrays.asList(writerMember.getId(), mentionedMember1.getId(), mentionedMember2.getId());
        List<Member> membersToReturn = Arrays.asList(writerMember, mentionedMember1, mentionedMember2);

        when(memberService.findMembersByIds(anyList())).thenReturn(membersToReturn);
        
        // when
        mentionService.saveMentions(testComment, mentionedMemberIds);

        // then
        // 작성자는 제외하고 멘션1, 멘션2만 저장되어야 함
        verify(mentionRepository, times(1)).save(any(Mention.class));
        verify(mentionRepository).save(argThat(mention -> mention.getMember().getId().equals(mentionedMember1.getId())));
        verify(mentionRepository).save(argThat(mention -> mention.getMember().getId().equals(mentionedMember2.getId())));
        verify(mentionRepository, never()).save(argThat(mention -> mention.getMember().getId().equals(writerMember.getId())));
    }
    
    @Test
    @DisplayName("성공: 멘션 저장 (중복 ID 처리 및 작성자 제외)")
    void saveMentions_Success_DuplicateAndWriterExcluded() {
        // given
        List<Long> mentionedMemberIds = Arrays.asList(mentionedMember1.getId(), writerMember.getId(), mentionedMember1.getId()); // 중복 및 작성자 포함
        List<Member> membersToReturn = Arrays.asList(mentionedMember1, writerMember); // MemberService에서 중복 처리된 결과

        when(memberService.findMembersByIds(anyList())).thenReturn(membersToReturn);
        
        // when
        mentionService.saveMentions(testComment, mentionedMemberIds);

        // then
        // 중복 제거 및 작성자 제외 후 mentionedMember1에 대해서만 save가 1번 호출되어야 함
        verify(mentionRepository, times(1)).save(any(Mention.class));
        verify(mentionRepository).save(argThat(mention -> mention.getMember().getId().equals(mentionedMember1.getId())));
        verify(mentionRepository, never()).save(argThat(mention -> mention.getMember().getId().equals(writerMember.getId())));
    }

    @Test
    @DisplayName("성공: 멘션 저장 (ID 목록이 null 또는 empty)")
    void saveMentions_NullOrEmptyIds_NoSave() {
        // when
        mentionService.saveMentions(testComment, null);
        mentionService.saveMentions(testComment, Collections.emptyList());

        // then
        verify(memberService, never()).findMembersByIds(anyList());
        verify(mentionRepository, never()).save(any(Mention.class));
    }

    @Test
    @DisplayName("성공: 댓글로 멘션 목록 조회")
    void getMentionsByComment_Success() {
        // given
        Mention mention1 = Mention.builder().Id(1L).comment(testComment).member(mentionedMember1).build();
        Mention mention2 = Mention.builder().Id(2L).comment(testComment).member(mentionedMember2).build();
        when(mentionRepository.findByComment(testComment)).thenReturn(Arrays.asList(mention1, mention2));

        // when
        List<CommentMentionDto> result = mentionService.getMentionsByComment(testComment);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMemberId()).isEqualTo(mentionedMember1.getId());
        assertThat(result.get(1).getName()).isEqualTo(mentionedMember2.getName());
    }

    @Test
    @DisplayName("성공: 멘션 업데이트 (새로운 멘션 포함)")
    void updateMentions_Success_WithNewMentions() {
        // given
        List<Long> newMentionedIds = Collections.singletonList(mentionedMember1.getId());
        when(memberService.findMembersByIds(anyList())).thenReturn(Collections.singletonList(mentionedMember1));

        // when
        mentionService.updateMentions(testComment, newMentionedIds);

        // then
        verify(mentionRepository).deleteByComment(testComment);
        verify(mentionRepository, times(1)).save(any(Mention.class)); // saveMentions 내부에서 호출됨
    }

    @Test
    @DisplayName("성공: 멘션 업데이트 (ID 목록이 null 또는 empty)")
    void updateMentions_NullOrEmptyIds_DeletesExisting() {
        // when
        mentionService.updateMentions(testComment, null);
        mentionService.updateMentions(testComment, Collections.emptyList());

        // then
        verify(mentionRepository, times(2)).deleteByComment(testComment); // 두 번 호출 (null, empty)
        verify(mentionRepository, never()).save(any(Mention.class));
    }

    @Test
    @DisplayName("성공: 댓글 ID로 멘션된 회원 ID 목록 조회")
    void getMentionedMemberIds_Success() {
        // given
        Long commentId = testComment.getId();
        when(mentionRepository.findMemberIdsByCommentId(commentId)).thenReturn(Arrays.asList(2L, 3L));

        // when
        List<Long> result = mentionService.getMentionedMemberIds(commentId);

        // then
        assertThat(result).containsExactly(2L, 3L);
    }
}
