package com.codehows.daehobe.dto.comment;

import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.member.Member;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {
    // 댓글 id
    private Long id;

    // 작성자
    private String writerName;

    // 작성자 직책
    private String writerJPName;

    // 본문
    private String content;

    // 생성시간
    private LocalDateTime createdAt;

    // 수정시간
    private LocalDateTime updatedAt;

    @JsonProperty("isDel")
    private Boolean del;

    public static CommentDto fromComment(Comment comment,String writerName, String writerJPName) {


        return CommentDto.builder()
                .id(comment.getId())
                .writerName(writerName)
                .writerJPName(writerJPName)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .del(comment.isDel())
                .build();
    }
}
