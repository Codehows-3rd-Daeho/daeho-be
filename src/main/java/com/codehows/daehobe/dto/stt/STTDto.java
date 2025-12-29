package com.codehows.daehobe.dto.stt;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.stt.STT;
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
    private String content;
    private String summary;
    private Long meetingId;
    private String status;
    private FileDto file;
    private Integer chunkingCnt;
    private Long memberId;
    private Integer progress;

    public void updateProgress(Integer progress) {
        this.progress = progress;
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
                .chunkingCnt(stt.getChunkingCnt())
                .status(String.valueOf(stt.getStatus()))
                .progress(stt.getProgress())
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
                .chunkingCnt(stt.getChunkingCnt())
                .status(String.valueOf(stt.getStatus()))
                .progress(stt.getProgress())
                .file(audioFile)
                .memberId(stt.getCreatedBy())
                .build();
    }

}
