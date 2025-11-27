package com.codehows.daehobe.dto.comment;

import java.time.LocalDateTime;

public class CommentDto {
    // 댓글 id
    private Long id;

    // 작성자
    private String writer;

    // 본문
    private String comment;

    // 생성시간
    private LocalDateTime createdAt;

    // 수정시간
    private LocalDateTime updatedAt;
}
