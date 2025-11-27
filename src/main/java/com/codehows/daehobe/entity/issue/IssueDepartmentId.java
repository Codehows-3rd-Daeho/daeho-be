package com.codehows.daehobe.entity.issue;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDepartmentId implements Serializable {
    private Long issueId;
    private Long departmentId;
}
