package com.codehows.daehobe.masterData.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CustomGroupMemberId implements Serializable {
    @Column(name = "custom_group_id")
    private Long customGroupId;

    @Column(name = "member_id")
    private Long memberId;
}
