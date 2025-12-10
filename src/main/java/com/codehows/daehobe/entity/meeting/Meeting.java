package com.codehows.daehobe.entity.meeting;

import com.codehows.daehobe.constant.Status;
import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.issue.Issue;
import com.codehows.daehobe.entity.masterData.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    private Long meetingId; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Issue issueId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category categoryId;

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
    private File fileId;

    @Column(name = "is_del", nullable = false)
    private boolean isDel = false;

    public void deleteMeeting(){
        this.isDel = true;
    }

    public void saveMeetingMinutes(File file) {
        this.fileId = file;
        this.status = Status.COMPLETED;
        this.endDate = LocalDate.now();
    }

    public void deleteMeetingMinutes() {
        this.fileId = null;
    }

}
