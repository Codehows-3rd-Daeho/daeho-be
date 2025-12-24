package com.codehows.daehobe.entity.log;

public interface Auditable<ID> {
    ID getId();

    default String getTitle() {
        return null;
    }
}
