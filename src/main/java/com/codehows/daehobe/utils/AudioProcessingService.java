package com.codehows.daehobe.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

            String inputPath = filePath.toAbsolutePath().toString().replace("\\", "/");
            String outputPath = tempPath.toAbsolutePath().toString().replace("\\", "/");

            log.info("Processing file: {}", inputPath);
            log.info("File size: {} bytes", Files.size(filePath));
            log.info("File exists: {}", Files.exists(filePath));

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", inputPath,
                    "-acodec", "copy",  // 오디오 코덱만 복사
                    "-vn",  // 비디오 스트림 제거
                    "-y",
                    outputPath
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("FFmpeg: {}", line);  // debug -> info로 변경해서 확실히 확인
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("✅ FFmpeg로 메타데이터 수정 완료");
            } else {
                log.error("❌ FFmpeg 실행 실패 (exit code: {})", exitCode);
                log.error("FFmpeg 전체 출력:\n{}", output.toString());
                Files.deleteIfExists(tempPath);
                throw new RuntimeException("FFmpeg 처리 실패");
            }

        } catch (Exception e) {
            log.error("FFmpeg 메타데이터 수정 실패", e);
            throw new RuntimeException("오디오 파일 처리 실패", e);
        }
    }
}