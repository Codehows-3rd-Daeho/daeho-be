package com.codehows.daehobe.entity.issue;

import com.codehows.daehobe.aop.AuditableField;
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
public class Issue extends BaseEntity implements Auditable<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long id; // PK

    @AuditableField(name="제목")
    @Column(name = "title", nullable = false)
    private String title;


    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

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


}
