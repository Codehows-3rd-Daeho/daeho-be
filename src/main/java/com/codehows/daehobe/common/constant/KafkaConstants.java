package com.codehows.daehobe.common.constant;

public final class KafkaConstants {

    private KafkaConstants() {
    }

    public static final String NOTIFICATION_TOPIC = "notification-topic";
    public static final String STT_ENCODING_TOPIC = "stt-encoding-topic";
    public static final String STT_PROCESSING_TOPIC = "stt-processing-topic";
    public static final String STT_SUMMARIZING_TOPIC = "stt-summarizing-topic";

    public static final String NOTIFICATION_GROUP = "notification-group";
    public static final String STT_ENCODING_GROUP = "stt-encoding-group";
    public static final String STT_PROCESSING_GROUP = "stt-processing-group";
    public static final String STT_SUMMARIZING_GROUP = "stt-summarizing-group";
}