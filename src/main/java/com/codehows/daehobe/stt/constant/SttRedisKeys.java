package com.codehows.daehobe.stt.constant;

public final class SttRedisKeys {

    private SttRedisKeys() {}

    public static final String STT_STATUS_HASH_PREFIX = "stt:status:";
    public static final String STT_RECORDING_HEARTBEAT_PREFIX = "stt:recording:heartbeat:";

    public static final String ABNORMAL_TERMINATION_LOCK_KEY = "stt:processor:lock:abnormal-termination";
    public static final String ENCODING_LOCK_KEY = "stt:processor:lock:encoding";
    public static final String PROCESSING_LOCK_KEY = "stt:processor:lock:processing";
    public static final String SUMMARIZING_LOCK_KEY = "stt:processor:lock:summarizing";
}
