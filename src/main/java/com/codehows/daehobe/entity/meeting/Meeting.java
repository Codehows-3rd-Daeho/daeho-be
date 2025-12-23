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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @AuditableField(name="참여자")
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingMember> meetingMembers = new ArrayList<>();

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
                case "제목" -> "제목 > " + title;
                case "내용" -> "내용 > " + content;
                case "상태" -> "상태 > " + status;
                case "카테고리" -> "카테고리 > " + category.getName();
                case "참여자" -> {
                    if (meetingMembers == null || meetingMembers.isEmpty()) {
                        yield "참여자 > 없음";
                    }
                    // 참여자 엔티티에서 멤버 이름을 꺼내 콤마(,)로 연결
                    String names = meetingMembers.stream()
                            .map(mm -> mm.getMember().getName())
                            .collect(Collectors.joining(", "));
                    yield "참여자 > [" + names + "]";
                }
                default -> null;
            };
        }
        return null;
    };
        @Override
        public String createLogMessage(ChangeType type) {
        return switch (type) {
            case CREATE -> "등록 > " + title;
            case DELETE -> "삭제 > " + title;
            default -> null;
        };
    }
}
