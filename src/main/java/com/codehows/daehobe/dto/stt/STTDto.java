package com.codehows.daehobe.dto.stt;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.stt.STT;
import lombok.Builder;
import lombok.Getter;

/*
프론트 반환용
 */

@Getter
@Builder
public class STTDto {
private Long id;
private String content;
private String summary;
private Long meetingId;
private String status;
private FileDto file;
private Integer chunkingCnt;

public static STTDto fromEntity(STT stt) {
    return STTDto.builder()
            .id(stt.getId())
            .content(stt.getContent())
            .summary(stt.getSummary())
            .meetingId(
                    stt.getMeeting() != null
                            ? stt.getMeeting().getId()
                            : null
            )
            .chunkingCnt(stt.getChunkingCnt())
            .status(String.valueOf(stt.getStatus()))
            .build();
}

public static STTDto fromEntity(STT stt, FileDto audioFile) {
    return STTDto.builder()
            .id(stt.getId())
            .content(stt.getContent())
            .summary(stt.getSummary())
            .meetingId(
                    stt.getMeeting() != null
                            ? stt.getMeeting().getId()
                            : null
            )
            .chunkingCnt(stt.getChunkingCnt())
            .status(String.valueOf(stt.getStatus()))
            .file(audioFile)
            .build();
}

}
