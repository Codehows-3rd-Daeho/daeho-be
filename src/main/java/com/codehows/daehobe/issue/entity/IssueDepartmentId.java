package com.codehows.daehobe.issue.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class IssueDepartmentId implements Serializable {
    private Long issue;
    private Long department;
}
