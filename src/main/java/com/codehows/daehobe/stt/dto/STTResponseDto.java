package com.codehows.daehobe.stt.dto;

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
    private Integer progress;
    private boolean completed;
    private String content;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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
        StringBuilder currentSpeakerText = new StringBuilder();

        for (STTResult result : sttResults) {
            if (result.getWords() == null) continue;

            for (STTResult.Word word : result.getWords()) {
                String speaker = word.getSpeaker();

                if (speaker != null && !speaker.equals(prevSpeaker)) {
                    // 이전 화자 내용 마무리
                    if (prevSpeaker != null) {
                        sb.append(renderSpeakerBlock(prevSpeaker, currentSpeakerText.toString().trim()));
                    }
                    currentSpeakerText = new StringBuilder();
                    prevSpeaker = speaker;
                }

                currentSpeakerText.append(word.getWord()).append(" ");
            }
        }

        // 마지막 화자 처리
        if (prevSpeaker != null) {
            sb.append(renderSpeakerBlock(prevSpeaker, currentSpeakerText.toString().trim()));
        }

        return sb.toString();
    }

    private String renderSpeakerBlock(String speaker, String text) {
        return String.format(
                """
                > **화자 %s**
                > 
                > %s
                >
                
                """,
                speaker.trim(), text.trim()
        );
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
