package com.codehows.daehobe.logging.AOP.interfaces;

import com.codehows.daehobe.logging.constant.ChangeType;

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
