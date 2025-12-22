package com.codehows.daehobe.service.comment;

import com.codehows.daehobe.dto.comment.CommentMentionDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.comment.Mention;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.commnet.MentionRepository;
import com.codehows.daehobe.service.member.MemberService;
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


}
