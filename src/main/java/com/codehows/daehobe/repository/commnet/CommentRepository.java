package com.codehows.daehobe.repository.commnet;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.comment.Comment;

import com.codehows.daehobe.entity.issue.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByIsDelFalse(Pageable pageable);

    Page<Comment> findByTargetIdAndTargetTypeAndIsDelFalse(Long targetId, TargetType targetType, Pageable pageable);


}
