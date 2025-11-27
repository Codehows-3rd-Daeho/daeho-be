package com.codehows.daehobe.entity.file;

import com.codehows.daehobe.entity.BaseEntity;
import com.codehows.daehobe.entity.meeting.Meeting;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stt")
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class STT extends BaseEntity {

    @Id
    @Column(name = "stt_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meetingId;
}
