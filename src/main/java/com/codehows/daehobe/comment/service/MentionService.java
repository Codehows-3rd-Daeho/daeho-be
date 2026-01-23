package com.codehows.daehobe.comment.service;

import com.codehows.daehobe.comment.dto.CommentMentionDto;
import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.comment.entity.Mention;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.comment.repository.MentionRepository;
import com.codehows.daehobe.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MentionService {
    private final MentionRepository mentionRepository;
    private final MemberService memberService;

    public void saveMentions(Comment comment, List<Long> mentionedMemberIds) {
        if (mentionedMemberIds == null || mentionedMemberIds.isEmpty()) return;

        List<Member> members = memberService.findMembersByIds(
                mentionedMemberIds.stream().distinct().toList()
        );

        members = members.stream()
                .filter(m -> !m.getId().equals(comment.getMember().getId()))
                .toList();

        for (Member member : members) {
            mentionRepository.save(
                    Mention.builder()
                            .comment(comment)
                            .member(member)
                            .build()
            );
        }
    }

    public List<CommentMentionDto> getMentionsByComment(Comment comment) {

        return mentionRepository.findByComment(comment).stream()
                .map(m -> new CommentMentionDto(
                        m.getMember().getId(),
                        m.getMember().getName()
                ))
                .toList();
    }

    public void updateMentions(Comment comment, List<Long> mentionedMemberIds) {
        mentionRepository.deleteByComment(comment);

        if (mentionedMemberIds != null && !mentionedMemberIds.isEmpty()) {
            saveMentions(comment, mentionedMemberIds);
        }
    }

    public List<Long> getMentionedMemberIds(Long commentId) {
        return mentionRepository.findMemberIdsByCommentId(commentId);
    }

}
