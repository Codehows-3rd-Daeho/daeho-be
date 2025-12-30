package com.codehows.daehobe.service.stt;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@AllArgsConstructor
public class DagloSttConfigService {

    private final ObjectMapper objectMapper;

    //post 요청 본문 JSON 생성
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
