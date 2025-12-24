package com.codehows.daehobe.dto.stt;

import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/*
다글로 응답 받는 용
 */
@Getter
@Setter
public class STTResponseDto {

    private String rid;
    private String status;

    @JsonProperty("sttResults")
    private List<STTResult> sttResults;

    //변환완료인지 체크
    public boolean isCompleted() {
        return "transcribed".equalsIgnoreCase(status);
    }

    // 변환된 전체 텍스트를 가져오는 메서드
    public String getContent() {
        if (sttResults == null || sttResults.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        String prevSpeaker = null;

        for (STTResult result : sttResults) {
            if (result.getWords() == null) continue;

            for (STTResult.Word word : result.getWords()) {
                String speaker = word.getSpeaker();

                // 화자가 바뀔 때만 speaker 표시
                if (speaker != null && !speaker.equals(prevSpeaker)) {
                    sb.append("\n(화자 ").append(speaker).append(")\n");
                    prevSpeaker = speaker;
                }

                sb.append(word.getWord());
            }
        }

        return sb.toString().trim();//trim(): 문자열 앞뒤 공백 제거
    }


    public STT toEntity(Meeting meeting) {
        STT stt = new STT();
        stt.setMeeting(meeting);
        // sttResults[0].transcript 값을 content에 넣음
        stt.setContent(this.getContent());
//        stt.setStatus(STT.Status.PROCESSING);
        return stt;
    }

    //응답 형태(객체 안의 객체 형태)
    @Getter
    @Setter
    public static class STTResult {
        private String transcript;
        private List<Word> words;
        private Object keywords;

        @Getter
        @Setter
        public static class Word {
            private String speaker;
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
