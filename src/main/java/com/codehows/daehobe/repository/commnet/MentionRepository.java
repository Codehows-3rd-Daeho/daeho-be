package com.codehows.daehobe.repository.commnet;

import com.codehows.daehobe.dto.comment.MentionMemberDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.comment.Mention;
import com.codehows.daehobe.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByComment(Comment comment);

    void deleteByComment(Comment comment);

    boolean existsByCommentAndMember(Comment comment, Member member);
}
