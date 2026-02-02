package com.codehows.daehobe.stt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttTranscriptionResult {
    private boolean completed;
    private String content;
    private int progress;

    public static SttTranscriptionResult from(STTResponseDto dagloResult) {
        if (dagloResult == null) {
            return SttTranscriptionResult.builder()
                    .completed(false)
                    .content("")
                    .progress(0)
                    .build();
        }
        return SttTranscriptionResult.builder()
                .completed(dagloResult.isCompleted())
                .content(dagloResult.getContent())
                .progress(dagloResult.getProgress())
                .build();
    }

    public static SttTranscriptionResult stillProcessing() {
        return SttTranscriptionResult.builder()
                .completed(false)
                .content("")
                .progress(0)
                .build();
    }
}
