package com.codehows.daehobe.stt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttSummaryResult {
    private boolean completed;
    private String summaryText;
    private int progress;

    public static SttSummaryResult from(SummaryResponseDto dagloResult) {
        if (dagloResult == null) {
            return SttSummaryResult.builder()
                    .completed(false)
                    .summaryText("")
                    .progress(0)
                    .build();
        }
        return SttSummaryResult.builder()
                .completed(dagloResult.isCompleted())
                .summaryText(dagloResult.getSummaryText())
                .progress(dagloResult.getProgress())
                .build();
    }

    public static SttSummaryResult stillProcessing() {
        return SttSummaryResult.builder()
                .completed(false)
                .summaryText("")
                .progress(0)
                .build();
    }
}
