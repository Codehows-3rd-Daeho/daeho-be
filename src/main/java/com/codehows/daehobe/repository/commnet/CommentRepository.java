package com.codehows.daehobe.repository.commnet;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.comment.Comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;



public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByIsDelFalse(Pageable pageable);

    Page<Comment> findByTargetIdAndTargetType(Long targetId, TargetType targetType, boolean isDel, Pageable pageable);
}
