package com.codehows.daehobe.entity.issue;
import com.codehows.daehobe.dto.IssueMemberDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issue_member")
@IdClass(IssueMemberId.class) // 복합키: member_id + issue_id
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueMember extends BaseEntity {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issueId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member memberId;



    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "is_permitted", nullable = false)
    private boolean isPermitted;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;


    public IssueMember(Issue issue, Member member) {
        this.issueId = issue;
        this.memberId = member;
    }


}

