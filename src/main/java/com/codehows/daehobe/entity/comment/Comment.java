package com.codehows.daehobe.entity.comment;

import com.codehows.daehobe.aop.AuditableField;
import com.codehows.daehobe.aop.Loggable;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.CommentRequest;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.log.Log;
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
public class Comment extends BaseEntity implements Auditable<Long>, Loggable {

    @Id
    @Column(name = "comment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @AuditableField(name="내용")
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

    @Override
    public String createLogMessage(ChangeType type, String fieldName) {
        if (type != ChangeType.UPDATE) {
            return null;
        }

        if ("내용".equals(fieldName)) {
            return "내용 > " + content;
        }

        return null;
    }


    @Override
    public String createLogMessage(ChangeType type) {
        return switch (type) {
            case CREATE -> "등록 > " + content;
            case DELETE -> "삭제 > " + content;
            default -> null;
        };
    }

}
