package com.codehows.daehobe.issue.entity;

import com.codehows.daehobe.logging.AOP.annotations.AuditableField;
import com.codehows.daehobe.logging.AOP.interfaces.Loggable;
import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.issue.dto.IssueFormDto;
import com.codehows.daehobe.common.entity.BaseEntity;
import com.codehows.daehobe.logging.AOP.interfaces.Auditable;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.common.constant.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "issue")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue extends BaseEntity implements Auditable<Long>, Loggable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long id; // PK

    @AuditableField(name="제목")
    @Column(name = "title", nullable = false)
    private String title;

    @AuditableField(name="내용")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @AuditableField(name="카테고리")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @AuditableField(name="시작일")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @AuditableField(name="마감일")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @AuditableField(name="상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "is_del", nullable = false)
    private boolean isDel;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate; // 비밀글 여부

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueMember> issueMembers;

    public void update(IssueFormDto issueFormDto, Category cate) {
        this.title = issueFormDto.getTitle();
        this.content = issueFormDto.getContent();
        this.category = cate;
        this.startDate = issueFormDto.getStartDate();
        this.endDate = issueFormDto.getEndDate();
        this.status = Status.valueOf(issueFormDto.getStatus());
        this.isPrivate = issueFormDto.getIsPrivate();
    }

    public void delete(){
        this.isDel = true;
    }

    @Override
    public String createLogMessage(ChangeType type, String fieldName) {
        if (type == ChangeType.UPDATE) {
            return switch (fieldName) {
                case "제목" -> "제목 > " + title;
                case "내용" -> "내용 > " + content;
                case "상태" -> "상태 > " + status;
                case "카테고리" -> "카테고리 > " + category.getName();
                default -> null;
            };
        }
        return null;
    }

    @Override
    public String createLogMessage(ChangeType type) {
        return switch (type) {
            case CREATE -> "등록 > " + title;
            case DELETE -> "삭제 > " + title;
            default -> null;
        };
    }



}
