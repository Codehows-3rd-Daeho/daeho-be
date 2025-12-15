package com.codehows.daehobe.dto.stt;

import com.codehows.daehobe.entity.file.STT;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/*
다글로 응답 받는 용
 */
@Getter
@Setter
@NoArgsConstructor
public class DagloSTTResponseDto {

    private String rid;
    private String status;

    @JsonProperty("sttResults")
    private List<STTResult> sttResults;

    public boolean isCompleted() {
        return "transcribed".equalsIgnoreCase(status);
    }

    // 변환된 전체 텍스트를 가져오는 편의 메서드
    public String getContent() {
        if (sttResults == null || sttResults.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (STTResult result : sttResults) {
            if (result.getTranscript() != null) {
                sb.append(result.getTranscript()).append(" ");
            }
        }
        return sb.toString().trim();//trim(): 문자열 앞뒤 공백 제거
    }


    public STT toEntity(Meeting meeting) {
        STT stt = new STT();
        stt.setMeeting(meeting);
        // sttResults[0].transcript 값을 content에 넣음
        stt.setContent(this.getContent());
        return stt;
    }

    @Getter
    @Setter
    public static class STTResult {
        private String transcript;
        private List<Word> words;
        private Object keywords;

        @Getter
        @Setter
        public static class Word {
            private String word;
            private Time startTime;
            private Time endTime;
            private String segmentId;

            @Getter
            @Setter
            public static class Time {
                private int nanos;
                private String seconds;
            }
        }
    }
}
