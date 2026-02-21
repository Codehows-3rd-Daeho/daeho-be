package com.codehows.daehobe.stt.service.listener;

import com.codehows.daehobe.stt.constant.SttRedisKeys;
import com.codehows.daehobe.stt.service.STTService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SttHeartbeatExpirationListener extends KeyExpirationEventMessageListener {

    private final STTService sttService;

    public SttHeartbeatExpirationListener(RedisMessageListenerContainer listenerContainer,
                           STTService sttService) {
        super(listenerContainer);
        this.sttService = sttService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (!expiredKey.startsWith(SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX)) {
            return;
        }

        String sttIdStr = expiredKey.substring(SttRedisKeys.STT_RECORDING_HEARTBEAT_PREFIX.length());
        Long sttId = Long.parseLong(sttIdStr);

        log.warn("Heartbeat expired for STT ID: {}. Triggering abnormal termination.", sttId);
        sttService.handleAbnormalTermination(sttId);
    }
}
