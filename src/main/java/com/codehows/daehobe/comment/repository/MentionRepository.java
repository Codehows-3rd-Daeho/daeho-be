package com.codehows.daehobe.comment.repository;

import com.codehows.daehobe.comment.entity.Comment;
import com.codehows.daehobe.comment.entity.Mention;
import com.codehows.daehobe.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByComment(Comment comment);

    void deleteByComment(Comment comment);

    boolean existsByCommentAndMember(Comment comment, Member member);

    @Query("""
                select m.member.id
                from Mention m
                where m.comment.id = :commentId
            """)
    List<Long> findMemberIdsByCommentId(Long commentId);
}
