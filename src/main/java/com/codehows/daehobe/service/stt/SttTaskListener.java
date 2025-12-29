package com.codehows.daehobe.service.stt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SttTaskListener {

    private final SttTaskProcessor sttTaskProcessor;

    /**
     * Redis Pub/Sub 채널로부터 메시지를 수신하여 비동기 처리를 위임합니다.
     * @param message 수신된 메시지 (사용되지는 않음, 신호용)
     * @param channel 수신된 채널 이름
     */
    public void handleMessage(String message, String channel) {
        log.info("Received message: {} from channel: {}", message, channel);
        sttTaskProcessor.processAllTasks();
    }
}