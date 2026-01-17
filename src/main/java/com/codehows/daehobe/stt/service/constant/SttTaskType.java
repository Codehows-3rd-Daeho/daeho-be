package com.codehows.daehobe.stt.service.constant;

public enum SttTaskType {
    ABNORMAL_TERMINATION("abnormal-termination"),
    ENCODING("encoding"),
    PROCESSING("processing"),
    SUMMARIZING("summarizing"),
    ALL("all");

    private final String value;

    SttTaskType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SttTaskType fromValue(String value) {
        for (SttTaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return ALL; // 기본값
    }
}