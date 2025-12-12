package com.codehows.daehobe.dto.comment;

import com.codehows.daehobe.constant.TargetType;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {
    private Long targetId;
    private TargetType targetType;
    private String content;
}
