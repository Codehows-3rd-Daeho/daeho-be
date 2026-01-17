package com.codehows.daehobe.stt.service.constant;

public final class SttRedisKeys {

    private SttRedisKeys() {}

    public static final String STT_PROCESSING_SET = "stt:processing";
    public static final String STT_SUMMARIZING_SET = "stt:summarizing";
    public static final String STT_ENCODING_SET = "stt:encoding";
    public static final String STT_RECORDING_SET = "stt:recording";

    public static final String STT_STATUS_HASH_PREFIX = "stt:status:";
    public static final String STT_RECORDING_SESSION = "stt:recording:session";

    public static final String ABNORMAL_TERMINATION_LOCK_KEY = "stt:processor:lock:abnormal-termination";
    public static final String ENCODING_LOCK_KEY = "stt:processor:lock:encoding";
    public static final String PROCESSING_LOCK_KEY = "stt:processor:lock:processing";
    public static final String SUMMARIZING_LOCK_KEY = "stt:processor:lock:summarizing";
}
