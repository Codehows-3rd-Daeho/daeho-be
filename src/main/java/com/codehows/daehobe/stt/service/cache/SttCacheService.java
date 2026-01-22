package com.codehows.daehobe.stt.service.cache;

import com.codehows.daehobe.stt.dto.STTDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttCacheService {
    private final StringRedisTemplate hashRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STT_STATUS_PREFIX = "stt:status:";

    public void cacheSttStatus(STTDto sttDto) {
        try {
            String key = STT_STATUS_PREFIX + sttDto.getId();
            String jsonValue = objectMapper.writeValueAsString(sttDto);

            hashRedisTemplate.opsForValue().set(key, jsonValue, 30, TimeUnit.MINUTES);

            log.debug("STT status cached - ID: {}", sttDto.getId());
        } catch (Exception e) {
            log.error("Failed to cache STT status for ID: {}", sttDto.getId(), e);
        }
    }

    public STTDto getCachedSttStatus(Long sttId) {
        try {
            String key = STT_STATUS_PREFIX + sttId;
            String jsonValue = hashRedisTemplate.opsForValue().get(key);

            return jsonValue != null ? objectMapper.readValue(jsonValue, STTDto.class) : null;
        } catch (Exception e) {
            log.error("Failed to get STT status for ID: {}", sttId, e);
            return null;
        }
    }
}
