package com.codehows.daehobe.constant;

public enum Status {
    PLANNED("진행전"),
    IN_PROGRESS("진행중"),
    COMPLETED("진행완료");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
