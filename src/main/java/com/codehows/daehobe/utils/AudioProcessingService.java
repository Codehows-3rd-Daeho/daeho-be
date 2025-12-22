package com.codehows.daehobe.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class AudioProcessingService {

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    public void fixAudioMetadata(Path filePath) {
        try {
            Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".temp");

            // Windows 경로를 FFmpeg가 이해할 수 있는 형태로 변환
            String inputPath = filePath.toAbsolutePath().toString().replace("\\", "/");
            String outputPath = tempPath.toAbsolutePath().toString().replace("\\", "/");

            log.info("Input: {}", inputPath);
            log.info("Output: {}", outputPath);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", inputPath,
                    "-c", "copy",
                    "-y",
                    outputPath
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // FFmpeg 출력 로그 (에러 메시지 확인용)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("FFmpeg로 메타데이터 수정 완료: {}", filePath);
            } else {
                log.error("FFmpeg 실행 실패 (exit code: {})", exitCode);
                log.error("FFmpeg 출력:\n{}", output.toString());
                Files.deleteIfExists(tempPath);
                throw new RuntimeException("FFmpeg 처리 실패: " + output.toString());
            }

        } catch (Exception e) {
            log.error("FFmpeg 메타데이터 수정 실패", e);
            throw new RuntimeException("오디오 파일 처리 실패", e);
        }
    }
}