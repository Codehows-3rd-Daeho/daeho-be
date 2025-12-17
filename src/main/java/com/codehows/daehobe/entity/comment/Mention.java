package com.codehows.daehobe.entity.comment;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mention")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mention extends BaseEntity {

    @Id
    @Column(name = "mention_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

}
