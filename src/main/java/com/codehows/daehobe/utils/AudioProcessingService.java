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

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", filePath.toAbsolutePath().toString(),
                    "-c", "copy",  // 코덱 복사 (재인코딩 안 함)
                    "-y",  // 덮어쓰기
                    tempPath.toAbsolutePath().toString()
            );

            // 에러 출력 로깅
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // FFmpeg 출력 로그
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("FFmpeg로 메타데이터 수정 완료: {}", filePath);
            } else {
                log.error("FFmpeg 실행 실패 (exit code: {})", exitCode);
                Files.deleteIfExists(tempPath);
                throw new RuntimeException("FFmpeg 처리 실패");
            }

        } catch (Exception e) {
            log.error("FFmpeg 메타데이터 수정 실패", e);
            throw new RuntimeException("오디오 파일 처리 실패", e);
        }
    }
}