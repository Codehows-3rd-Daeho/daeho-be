package com.codehows.daehobe.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CommentMentionDto {
    private Long memberId;
    private String name;
}
