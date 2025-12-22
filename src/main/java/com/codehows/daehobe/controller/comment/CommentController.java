package com.codehows.daehobe.controller.comment;

import com.codehows.daehobe.dto.comment.CommentDto;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.dto.issue.IssueFormDto;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<?> createIssueComment(
            @PathVariable Long id,
            @RequestPart("data") CommentRequest dto,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles,
            Authentication authentication
    ) {
        Long memberId = Long.valueOf(authentication.getName());
        Comment saved =
                commentService.createIssueComment(id, dto, memberId, multipartFiles);
        return ResponseEntity.ok(saved);
    }

    // 수정
    @PutMapping("/comment/{id}")
    public ResponseEntity<?> updateComment(
            @PathVariable Long id,
            @RequestPart("data") CommentRequest dto,
            @RequestPart(value = "file", required = false) List<MultipartFile> filesToUpload
    ) {
        try {
            List<MultipartFile> newFiles =
                    filesToUpload != null ? filesToUpload : List.of();

            List<Long> removeFileIds =
                    dto.getRemoveFileIds() != null ? dto.getRemoveFileIds() : List.of();

            commentService.updateComment(id, dto, newFiles, removeFileIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("댓글 수정 중 오류 발생");
        }
    }


    // 삭제
    @DeleteMapping("/comment/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id) {
        try {
            commentService.deleteComment(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("댓글 삭제 중 오류 발생");
        }
    }


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
    public ResponseEntity<?> createMeetingComment(
            @PathVariable Long id,
            @RequestPart("data") CommentRequest dto,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles,
            Authentication authentication
    ) {
        Long memberId = Long.valueOf(authentication.getName());
        Comment saved =
                commentService.createMeetingComment(id, dto, memberId, multipartFiles);
        return ResponseEntity.ok(saved);
    }
}
