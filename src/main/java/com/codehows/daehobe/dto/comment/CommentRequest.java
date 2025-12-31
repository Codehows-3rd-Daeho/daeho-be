package com.codehows.daehobe.dto.comment;

import com.codehows.daehobe.constant.TargetType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {
    private Long targetId;
    private TargetType targetType;
    private String content;
    private List<Long> mentionedMemberIds; // 멘션
    private List<Long> removeFileIds;
}
