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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AudioProcessingService {

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    // 정규식 패턴: time=HH:MM:SS.mm 형식 추출
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    /**
     * FFmpeg 출력 라인에서 시간을 밀리초(Long)로 변환
     * @param line FFmpeg 출력 라인 (예: "size=123213 time=00:00:08.16 bitrate=...")
     * @return 시간(밀리초), 추출 실패 시 null
     */
    public static Long extractTimeInMillis(String line) {
        Matcher matcher = TIME_PATTERN.matcher(line);

        if (matcher.find()) {
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            int seconds = Integer.parseInt(matcher.group(3));
            int centiseconds = Integer.parseInt(matcher.group(4)); // 1/100초

            // 밀리초로 변환
            long millis = (hours * 3600L + minutes * 60L + seconds) * 1000L + centiseconds * 10L;
            return millis;
        }

        return null;
    }

    /**
     * 초 단위로 변환 (소수점 포함)
     */
    public static Double extractTimeInSeconds(String line) {
        Long millis = extractTimeInMillis(line);
        return millis != null ? millis / 1000.0 : null;
    }

    public Long fixAudioMetadata(Path filePath) {
        long recordingTime = 0L;
        String fileName = filePath.getFileName().toString();
        String fileNameWithoutExtension = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf("."))
                : fileName;
        try {
            Path tempPath = filePath.resolveSibling(fileNameWithoutExtension + ".temp.wav");

            String inputPath = filePath.toAbsolutePath().toString().replace("\\", "/");
            String outputPath = tempPath.toAbsolutePath().toString().replace("\\", "/");

            log.info("Processing file: {}", inputPath);
            log.info("File size: {} bytes", Files.size(filePath));
            log.info("File exists: {}", Files.exists(filePath));

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
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("Input #0") || line.contains("Output #0") || line.contains("size=")) {
                        log.info("FFmpeg: {}", line);
                        Long currentTime = extractTimeInMillis(line);
                        if (currentTime != null) {
                            System.out.println("현재 진행 시간: " + currentTime + "ms");
                            recordingTime = currentTime;
                        }
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("[FFmpeg] 메타데이터 수정 완료");
            } else {
                log.error("[FFmpeg] 실행 실패 (exit code: {})", exitCode);
                log.error("[FFmpeg] 전체 출력:\n{}", output.toString());
                Files.deleteIfExists(tempPath);
                throw new RuntimeException("[FFmpeg] 처리 실패");
            }

        } catch (Exception e) {
            log.error("[FFmpeg] 메타데이터 수정 실패", e);
            try{
                Files.deleteIfExists(filePath.resolveSibling(fileNameWithoutExtension + ".temp.wav"));
            }catch (Exception ex){
                ex.printStackTrace();
            }
            throw new RuntimeException("오디오 파일 처리 실패", e);
        }
        return recordingTime;
    }
}