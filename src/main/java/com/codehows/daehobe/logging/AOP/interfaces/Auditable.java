package com.codehows.daehobe.logging.AOP.interfaces;

public interface Auditable<ID> {
    ID getId();

    default String getTitle() {
        return null;
    }
}
