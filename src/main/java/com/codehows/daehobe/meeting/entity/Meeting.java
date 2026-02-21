package com.codehows.daehobe.meeting.entity;

import com.codehows.daehobe.logging.AOP.annotations.AuditableField;
import com.codehows.daehobe.logging.AOP.interfaces.Loggable;
import com.codehows.daehobe.common.constant.Status;
import com.codehows.daehobe.meeting.dto.MeetingFormDto;
import com.codehows.daehobe.common.entity.BaseEntity;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.logging.AOP.interfaces.Auditable;
import com.codehows.daehobe.masterData.entity.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @AuditableField(name="내용")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @AuditableField(name="카테고리")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @AuditableField(name="시작일")
    @Column(name = "start_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;

    @AuditableField(name="마감일")
    @Column(name = "end_date", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endDate;

    @AuditableField(name="상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    // 회의록 id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private File file;

    @Column(name = "is_del", nullable = false)
    private boolean isDel;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingMember> meetingMembers;

    @AuditableField(name="비고")
    @Column(name = "remarks")
    private String remarks;

    @Column(name = "color")
    private String color;

    public void updateColor(String color) {
        this.color = color;
    }

    public void deleteMeeting() {
        this.isDel = true;
    }

    public void saveMeetingMinutes(File file) {
        this.file = file;
    }

    public void update(MeetingFormDto meetingFormDto, Category category, Issue issue) {
        this.issue = issue;
        this.title = meetingFormDto.getTitle();
        this.content = meetingFormDto.getContent();
        this.category = category;
        this.startDate = meetingFormDto.getStartDate();
        this.endDate = meetingFormDto.getEndDate();
        this.status = Status.valueOf(meetingFormDto.getStatus());
        this.isPrivate = meetingFormDto.getIsPrivate();
        this.remarks = meetingFormDto.getRemarks();
    }

    public void updateEndDate() {
        this.endDate = LocalDateTime.now();
    }

    public void clearEndDate() {
        this.endDate = null;
    }

}
