package com.codehows.daehobe.dto.comment;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.member.Member;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {
    // 댓글 id
    private Long id;

    // 작성자 id
    private Long writerMemberId;

    // 작성자
    private String writerName;

    // 작성자 직책
    private String writerJPName;

    // 첨부파일
    private List<FileDto> fileList;

    // 본문
    private String content;

    // 생성시간
    private LocalDateTime createdAt;

    // 수정시간
    private LocalDateTime updatedAt;

    // 멘션
    private List<CommentMentionDto> mentions;


    @JsonProperty("isDel")
    private Boolean del;

    public static CommentDto fromComment(Comment comment,String writerName, String writerJPName, List<FileDto> fileList, Long writerMemberId, List<CommentMentionDto> mentions) {


        return CommentDto.builder()
                .id(comment.getId())
                .writerName(writerName)
                .writerJPName(writerJPName)
                .writerMemberId(writerMemberId)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .mentions(mentions)
                .fileList(fileList)
                .del(comment.isDel())
                .build();
    }
}
