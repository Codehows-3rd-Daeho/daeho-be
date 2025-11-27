package com.codehows.daehobe.entity.comment;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;

public class Mention extends BaseEntity {

    @Id
    @Column(name = "mention_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member memberId;

}
