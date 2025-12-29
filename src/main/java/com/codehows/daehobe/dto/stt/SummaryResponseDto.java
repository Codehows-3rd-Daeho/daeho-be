package com.codehows.daehobe.dto.stt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SummaryResponseDto {

    private String rid;
    private String status;
    private int progress;
    private String title;
    private List<Minute> minutes;

    public boolean isCompleted() {
        return "processed".equalsIgnoreCase(status) && progress == 100;
    }

    //문자 병합
    public String getSummaryText() {
        if (minutes == null || minutes.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();

        if (title != null) {
            sb.append(title).append("\n\n");
        }

        for (Minute minute : minutes) {
            sb.append("[").append(minute.getTitle()).append("]\n");

            for (Bullet bullet : minute.getBullets()) {
                sb.append(bullet.isImportant() ? "-**" : "- ");
                sb.append(bullet.getText()).append("\n");
            }
            sb.append("\n");
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
