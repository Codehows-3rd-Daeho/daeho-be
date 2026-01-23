package com.codehows.daehobe.issue.entity;
import com.codehows.daehobe.common.entity.BaseEntity;
import com.codehows.daehobe.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_member")
@IdClass(IssueMemberId.class) // 복합키: member_id + issue_id
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueMember extends BaseEntity {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "is_permitted", nullable = false)
    private boolean isPermitted;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    public void updateIsRead(boolean isRead){
        this.isRead = isRead;
    }

}

