package com.codehows.daehobe.entity.meeting;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.issue.IssueMemberId;
import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meeting_member")
@IdClass(IssueMemberId.class) // 복합키: member_id + issue_id
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMember extends BaseEntity {

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "meeting_id", nullable = false)
        private Meeting meetingId;

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

}
