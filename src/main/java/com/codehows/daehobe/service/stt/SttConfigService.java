package com.codehows.daehobe.service.stt;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@AllArgsConstructor
// JSON 생성
public class SttConfigService {

    private final ObjectMapper objectMapper;

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "speakerDiarization", Map.of(
                                    "enable",true
                            )
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException("sttConfig JSON 생성 실패", e);
        }
    }
}
