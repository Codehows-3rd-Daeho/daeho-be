package com.codehows.daehobe.entity.meeting;

import com.codehows.daehobe.aop.AuditableField;
import com.codehows.daehobe.aop.Loggable;
import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.dto.meeting.MeetingFormDto;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.masterData.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "meeting")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting extends BaseEntity implements Auditable<Long>, Loggable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @AuditableField(name="제목")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "start_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    // 회의록 id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private File file;

    @Column(name = "is_del", nullable = false)
    private boolean isDel;

    public void deleteMeeting() {
        this.isDel = true;
    }

    public void saveMeetingMinutes(File file) {
        this.file = file;
        this.status = Status.COMPLETED;
        this.endDate = LocalDateTime.now();
    }

    public void deleteMeetingMinutes() {
        this.file = null;
    }

    public void update(MeetingFormDto meetingFormDto, Category category, Issue issue) {
        this.issue = issue;
        this.title = meetingFormDto.getTitle();
        this.content = meetingFormDto.getContent();
        this.category = category;
        this.startDate = meetingFormDto.getStartDate();
        this.endDate = meetingFormDto.getEndDate();
        this.status = Status.valueOf(meetingFormDto.getStatus());
    }

    public void updateEndDate() {
        this.endDate = LocalDateTime.now();
    }

    public void clearEndDate() {
        this.endDate = null;
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

        return switch (type) {
            case CREATE -> "등록 > " + title;
            case DELETE -> "삭제 > " + title;
            default -> null;
        };
    }
}
