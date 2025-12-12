//package com.codehows.daehobe.service.comment;
//
//import com.codehows.daehobe.constant.TargetType;
//import com.codehows.daehobe.dto.comment.CommentDto;
//import com.codehows.daehobe.entity.comment.Comment;
//import com.codehows.daehobe.repository.commnet.CommentRepository;
//import com.codehows.daehobe.service.issue.IssueService;
//import com.codehows.daehobe.service.member.MemberService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@Transactional
//@RequiredArgsConstructor
//public class CommentService {
//    private final CommentRepository commentRepository;
//    private final MemberService memberService;
//    private final IssueService issueService;
//
//    // 이슈 댓글 호출
//    public  Page<CommentDto> getCommentsByIssueId(Long issueId, Pageable pageable) {
//        Page<Comment> comments = commentRepository.findByTargetIdAndTargetType(issueId, TargetType.ISSUE, false, pageable);
//
//        return comments.map(comment -> {
//            String writerName = memberService.findMemberNameById(comment.getCreatedBy());
//            return CommentDto.fromComment(comment, writerName);
//        });
//    }
//
//    // 댓글 작성
//    public Comment createComment(){
//        TargetType targetType = TargetType.IS;
//
//
//    }
//}
