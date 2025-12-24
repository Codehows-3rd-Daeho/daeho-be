package com.codehows.daehobe.aop;

import com.codehows.daehobe.constant.ChangeType;

public interface Loggable {
    // CREATE, DELETE
    default String createLogMessage(ChangeType type) {
        return null;
    }

    // UPDATE
    default String createLogMessage(ChangeType type, String fieldName) {
        return null;
    }
}
