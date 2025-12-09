package com.codehows.daehobe.entity.issue;

import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.masterData.Category;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Entity
@Table(name = "issue")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long issueId; // PK

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category categoryId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "is_del", nullable = false)
    private boolean isDel = false;

    public void update(IssueDto issueDto, Category cate) {
        this.title = issueDto.getTitle();
        this.content = issueDto.getContent();
        this.categoryId = cate;
        this.startDate = issueDto.getStartDate();
        this.endDate = issueDto.getEndDate();
        this.status = Status.valueOf(issueDto.getStatus());
    }

    public void delete(){
        this.isDel = true;
    }


}
