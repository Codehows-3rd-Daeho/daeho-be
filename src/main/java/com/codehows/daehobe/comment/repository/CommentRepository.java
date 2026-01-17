package com.codehows.daehobe.comment.repository;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.comment.entity.Comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByIsDelFalse(Pageable pageable);

    Page<Comment> findByTargetIdAndTargetTypeAndIsDelFalse(Long targetId, TargetType targetType, Pageable pageable);


}
