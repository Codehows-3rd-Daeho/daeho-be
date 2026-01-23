package com.codehows.daehobe.stt.entity;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.common.entity.BaseEntity;
import com.codehows.daehobe.meeting.entity.Meeting;
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

    @Column(name = "rid", unique = true)
    private String rid;

    @Column(name = "summary_rid", unique = true)
    private String summaryRid;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    private Status status; // e.g., "RECORDING", "PROCESSING", "SUMMARIZING", "COMPLETED"

    @Column(name = "chunking_cnt")
    private int chunkingCnt;

    public enum Status {
        RECORDING, ENCODING, ENCODED, PROCESSING, SUMMARIZING, COMPLETED
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    public void countChunk() {
        this.chunkingCnt++;
    }

    public void updateFromDto(STTDto sttDto) {
        this.rid = sttDto.getRid();
        this.summaryRid = sttDto.getSummaryRid();
        this.content = sttDto.getContent();
        this.summary = sttDto.getSummary();
        this.chunkingCnt = sttDto.getChunkingCnt();
        this.status = sttDto.getStatus();
    }
}
