package com.codehows.daehobe.entity.stt;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.meeting.Meeting;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stt")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class STT extends BaseEntity {

    @Id
    @Column(name = "stt_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    private Long recordingTime;

    private Status status; // e.g., "RECORDING", "PROCESSING", "COMPLETED"

    public enum Status {
        RECORDING, PROCESSING, COMPLETED
    }

    //stt 변환 후 요약시 update로 값 추가
    public void updateSummary(String summary) {
        this.summary = summary;
    }
}
