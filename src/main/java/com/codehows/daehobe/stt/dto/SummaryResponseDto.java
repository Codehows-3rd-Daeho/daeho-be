package com.codehows.daehobe.stt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SummaryResponseDto {

    private String rid;
    private String status;
    private Integer progress;
    private String title;
    private boolean completed;
    private String summaryText;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Minute> minutes;

    public boolean isCompleted() {
        return "processed".equalsIgnoreCase(status) && progress == 100;
    }

    //문자 병합
    public String getSummaryText() {
        if (minutes == null || minutes.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();

        // 메인 제목 (## H2 레벨)
        if (title != null) {
            sb.append("## ").append(title).append("\n\n");
        }

        for (Minute minute : minutes) {
            // 섹션 제목 (### H3 레벨)
            String sectionTitle = minute.getTitle();
            sb.append("### ").append(sectionTitle).append("\n\n");

            for (Bullet bullet : minute.getBullets()) {
                String text = bullet.getText();

                if (bullet.isImportant()) {
                    // 중요한 항목: 굵은 글씨 + 불릿
                    sb.append("- **").append(text).append("**\n");
                } else {
                    // 일반 항목: 일반 불릿
                    sb.append("- ").append(text).append("\n");
                }
            }
            sb.append("\n"); // 섹션 간 여백
        }

        return sb.toString().trim();
    }


    @Getter
    @Setter
    public static class Minute {
        private String title;
        private List<Bullet> bullets;
    }

    @Getter
    @Setter
    public static class Bullet {
        @JsonProperty("isImportant")
        private boolean important;
        private String text;

    }
}
