package com.codehows.daehobe.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FFmpegConfig {

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @PostConstruct
    public void validateFFmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("FFmpeg 사용 가능: {}", ffmpegPath);
            } else {
                log.warn("FFmpeg 실행 실패");
            }
        } catch (Exception e) {
            log.error("FFmpeg를 찾을 수 없습니다. 설치가 필요합니다.", e);
        }
    }
}