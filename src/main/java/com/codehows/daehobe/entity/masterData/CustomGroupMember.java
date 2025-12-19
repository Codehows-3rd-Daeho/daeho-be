package com.codehows.daehobe.entity.masterData;

import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_group_member")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomGroupMember {
    @EmbeddedId
    private CustomGroupMemberId id;

    @MapsId("customGroupId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_group_id", nullable = false)
    private CustomGroup customGroup;

    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

}
