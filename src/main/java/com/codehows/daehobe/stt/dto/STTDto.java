package com.codehows.daehobe.stt.dto;

import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.stt.entity.STT;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
프론트 반환용
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class STTDto {
    private Long id;
    private String rid;
    private String summaryRid;
    private String content;
    private String summary;
    private Long meetingId;
    private STT.Status status;
    private FileDto file;
    private Long memberId;
    private Integer progress;
    private Integer retryCount;

    public void updateRid(String rid) {
        this.rid = rid;
    }

    public void updateSummaryRid(String summaryRid) {
        this.summaryRid = summaryRid;
    }

    public void updateProgress(Integer progress) {
        this.progress = progress;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    public void updateStatus(STT.Status status) {
        this.status = status;
    }

    public static STTDto fromEntity(STT stt) {
        return STTDto.builder()
                .id(stt.getId())
                .rid(stt.getRid())
                .content(stt.getContent())
                .summary(stt.getSummary())
                .meetingId(
                        stt.getMeeting() != null
                                ? stt.getMeeting().getId()
                                : null
                )
                .status(stt.getStatus())
                .memberId(stt.getCreatedBy())
                .build();
    }

    public static STTDto fromEntity(STT stt, FileDto audioFile) {
        return STTDto.builder()
                .id(stt.getId())
                .rid(stt.getRid())
                .content(stt.getContent())
                .summary(stt.getSummary())
                .meetingId(
                        stt.getMeeting() != null
                                ? stt.getMeeting().getId()
                                : null
                )
                .status(stt.getStatus())
                .file(audioFile)
                .memberId(stt.getCreatedBy())
                .build();
    }

    public void updateFile(FileDto file) {
        this.file = file;
    }

    public void updateRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }
}
