package com.codehows.daehobe.stt.constant;

public final class SttRedisKeys {

    private SttRedisKeys() {}

    public static final String STT_STATUS_HASH_PREFIX = "stt:status:";
    public static final String STT_RECORDING_HEARTBEAT_PREFIX = "stt:recording:heartbeat:";
    public static final String STT_POLLING_PROCESSING_SET = "stt:polling:processing";
    public static final String STT_POLLING_SUMMARIZING_SET = "stt:polling:summarizing";
    public static final String STT_RETRY_COUNT_PREFIX = "stt:retry:";
}
