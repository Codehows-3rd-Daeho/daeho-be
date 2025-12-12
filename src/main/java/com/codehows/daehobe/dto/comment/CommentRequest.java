package com.codehows.daehobe.dto.comment;

import com.codehows.daehobe.constant.TargetType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class CommentRequest {
    private Long targetId;
    private TargetType targetType;
    private String content;
}
