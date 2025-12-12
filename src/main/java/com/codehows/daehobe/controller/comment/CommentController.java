//package com.codehows.daehobe.controller.comment;
//
//import com.codehows.daehobe.controller.issue.IssueController;
//import com.codehows.daehobe.dto.comment.CommentDto;
//import com.codehows.daehobe.dto.issue.IssueListDto;
//import com.codehows.daehobe.service.comment.CommentService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequiredArgsConstructor
//public class CommentController {
//
//    private final CommentService commentService;
//
//    // issue 댓글
//    @GetMapping("/issue/{id}/comments")
//    public ResponseEntity<?> getCommentsByIssueId(
//            @PathVariable Long id,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        try {
//            Pageable pageable = PageRequest.of(page, size);
//            Page<CommentDto> dtoList = commentService.getCommentsByIssueId(id, pageable);
//            return ResponseEntity.ok(dtoList);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
//        }
//    };
//
//    @PostMapping("/issue/{id}/comment")
//    public ResponseEntity<?> createComment(){};
//}
