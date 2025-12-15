package com.codehows.daehobe.controller.comment;

import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.service.comment.CommentService;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // issue 댓글
    @GetMapping("/issue/{id}/comments")
    public ResponseEntity<?> getCommentsByIssueId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<CommentDto> dtoList = commentService.getCommentsByIssueId(id, pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }

    ;

    @PostMapping("/issue/{id}/comment")
    public ResponseEntity<?> createIssueComment(@RequestBody CommentRequest dto, @PathVariable Long id, Authentication authentication) {
        Long memberId = Long.valueOf(authentication.getName());

        CommentDto saved = commentService.createIssueComment(id, dto, memberId);

        return ResponseEntity.ok(saved);
    }

    ;

    // 회의 댓글
    @GetMapping("/meeting/{id}/comments")
    public ResponseEntity<?> getCommentsByMeetingId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<CommentDto> dtoList = commentService.getCommentsByMeetingId(id, pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("이슈 조회 중 오류 발생");
        }
    }

    ;

    @PostMapping("/meeting/{id}/comment")
    public ResponseEntity<?> createMeetingComment(@RequestBody CommentRequest dto, @PathVariable Long id, Authentication authentication) {
        String memberId = authentication.getName();

        CommentDto comment = commentService.createMeetingComment(id, dto, Long.valueOf(memberId));

        return ResponseEntity.ok(comment);
    }
}
