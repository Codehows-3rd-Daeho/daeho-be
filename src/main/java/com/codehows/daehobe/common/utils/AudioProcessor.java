package com.codehows.daehobe.common.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class AudioProcessor {

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    public void fixAudioMetadata(Path inputPath, Path outputPath) {
        try {
            String inputAbsPath = inputPath.toAbsolutePath().toString().replace("\\", "/");
            String outputAbsPath = outputPath.toAbsolutePath().toString().replace("\\", "/");

            log.info("Processing file: {}", inputAbsPath);
            log.info("File size: {} bytes", Files.size(inputPath));
            log.info("File exists: {}", Files.exists(inputPath));

            Process process = getProcessForEncode(inputAbsPath, outputAbsPath);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("Input #0") || line.contains("Output #0") || line.contains("size=")) {
                        log.info("FFmpeg: {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("[FFmpeg] 인코딩 완료: {}", outputAbsPath);
            } else {
                log.error("[FFmpeg] 실행 실패 (exit code: {})", exitCode);
                log.error("[FFmpeg] 전체 출력:\n{}", output);
                Files.deleteIfExists(outputPath);
                throw new RuntimeException("인코딩 실패");
            }

        } catch (Exception e) {
            log.error("[FFmpeg] 인코딩 실패", e);
            try{
                Files.deleteIfExists(outputPath);
            }catch (Exception ex){
                ex.printStackTrace();
            }
            throw new RuntimeException("오디오 파일 처리 실패", e);
        }
    }

    private Process getProcessForEncode(String inputPath, String outputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", inputPath,
                "-c:a", "pcm_s16le",     // Opus → 16비트 PCM (WAV 표준)
                "-ar", "48000",          // 샘플레이트 48kHz (Opus 표준)
                "-ac", "2",              // 스테레오
                "-vn",                    // 비디오 스트림 제거
                "-f", "wav",              // 출력 형식 명시적 WAV 지정
                "-map_metadata", "0",     // 메타데이터 복사
                "-y",                     // 덮어쓰기
                "-threads", "0",          // 모든 CPU 코어 사용
                outputPath
        );

        pb.redirectErrorStream(true);
        return pb.start();
    }
}