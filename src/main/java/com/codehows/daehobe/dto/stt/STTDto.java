package com.codehows.daehobe.dto.stt;

import com.codehows.daehobe.dto.issue.IssueFormDto;
import com.codehows.daehobe.entity.file.STT;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
프론트 반환용
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class STTDto {
private Long id;
private String content;
private String summary;
private Long meetingId;

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
                .build();
    }

}
