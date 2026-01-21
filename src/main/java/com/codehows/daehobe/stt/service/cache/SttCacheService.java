package com.codehows.daehobe.stt.service.cache;

import com.codehows.daehobe.stt.dto.STTDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.STT_STATUS_HASH_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheSttStatus(STTDto sttDto) {
        try {
            String key = STT_STATUS_HASH_PREFIX + sttDto.getId();
            Map<String, Object> data = objectMapper.convertValue(sttDto, Map.class);
            redisTemplate.opsForHash().putAll(key, data);
            redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to cache STT status for ID: {}", sttDto.getId(), e);
        }
    }

    public STTDto getCachedSttStatus(Long sttId) {
        try {
            String key = STT_STATUS_HASH_PREFIX + sttId;
            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
            if (data.isEmpty()) {
                return null;
            }
            return objectMapper.convertValue(data, STTDto.class);
        } catch (Exception e) {
            log.error("Failed to retrieve cached STT status for ID: {}", sttId, e);
            return null;
        }
    }

    public void updateCacheField(Long sttId, String fieldName, Object value) {
        try {
            String key = STT_STATUS_HASH_PREFIX + sttId;
            redisTemplate.opsForHash().put(key, fieldName, value);
        } catch (Exception e) {
            log.error("Failed to update cache field '{}' for STT ID: {}", fieldName, sttId, e);
        }
    }

    public void updateCacheFields(Long sttId, Map<String, Object> fields) {
        try {
            String key = STT_STATUS_HASH_PREFIX + sttId;
            redisTemplate.opsForHash().putAll(key, fields);
        } catch (Exception e) {
            log.error("Failed to update cache fields for STT ID: {}", sttId, e);
        }
    }
}
