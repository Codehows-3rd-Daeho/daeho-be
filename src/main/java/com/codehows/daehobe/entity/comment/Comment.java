package com.codehows.daehobe.entity.comment;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.entity.member.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment extends BaseEntity {

    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 이슈/회의 id
    @Column(nullable = false)
    private Long targetId;

    // 타겟 타입
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    // 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 본문
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 삭제상태 기본 false
    private boolean isDel;

    public void update(CommentRequest dto) {
        this.content = dto.getContent();
    }

    public void delete(){
        this.isDel = true;
    }

}
