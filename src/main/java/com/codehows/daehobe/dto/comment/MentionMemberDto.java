package com.codehows.daehobe.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MentionMemberDto {
    private Long id;
    private String name;
    private String jobPositionName;
    private String departmentName;
}
