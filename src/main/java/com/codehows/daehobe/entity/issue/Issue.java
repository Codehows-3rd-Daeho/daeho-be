package com.codehows.daehobe.entity.issue;

import com.codehows.daehobe.aop.AuditableField;
import com.codehows.daehobe.aop.Loggable;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.constant.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    public void update(IssueFormDto issueFormDto, Category cate) {
        this.title = issueFormDto.getTitle();
        this.content = issueFormDto.getContent();
        this.category = cate;
        this.startDate = issueFormDto.getStartDate();
        this.endDate = issueFormDto.getEndDate();
        this.status = Status.valueOf(issueFormDto.getStatus());
    }

    public void delete(){
        this.isDel = true;
    }

    @Override
    public String createLogMessage(ChangeType type, String fieldName) {
        if (type == ChangeType.UPDATE) {
            return switch (fieldName) {
                case "제목" -> "수정 > " + title;
                case "내용" -> "수정 > " + content;
                case "상태" -> "수정 > " + status;
                case "카테고리" -> "수정 > " + category.getName();
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
