package com.codehows.daehobe.utils;

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
    @Value("${ffprobe.path:/usr/bin/ffprobe}")
    private String ffprobePath;

    public void fixAudioMetadata(Path filePath) {
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

            Process process = getProcessForEncode(inputPath, outputPath);

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
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                log.info("[FFmpeg] 메타데이터 수정 완료");
            } else {
                log.error("[FFmpeg] 실행 실패 (exit code: {})", exitCode);
                log.error("[FFmpeg] 전체 출력:\n{}", output);
                Files.deleteIfExists(tempPath);
                throw new RuntimeException("인코딩 실패");
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
    }

    public AudioValidationResult validateAudioFile(Path filePath) {
        AudioValidationResult result = new AudioValidationResult();

        try {
            // 1. 파일 존재 및 크기 확인
            if (!Files.exists(filePath)) {
                result.setValid(false);
                result.setErrorMessage("파일이 존재하지 않습니다.");
                return result;
            }

            long fileSize = Files.size(filePath);
            if (fileSize == 0) {
                result.setValid(false);
                result.setErrorMessage("파일 크기가 0바이트입니다.");
                return result;
            }
            result.setFileSize(fileSize);

            // 2. FFprobe로 상세 정보 추출
            AudioInfo audioInfo = extractAudioInfo(filePath);
            result.setAudioInfo(audioInfo);

            // 3. 기대값 검증
            boolean isValid = validateAudioSpecs(audioInfo);
            result.setValid(isValid);

            if (!isValid) {
                result.setErrorMessage("오디오 스펙이 기대값과 일치하지 않습니다.");
            }

            log.info("[검증 완료] 파일: {}, 유효: {}, 코덱: {}, 샘플레이트: {}, 채널: {}",
                    filePath.getFileName(), isValid,
                    audioInfo.getCodec(), audioInfo.getSampleRate(), audioInfo.getChannels());

        } catch (Exception e) {
            log.error("[검증 실패] 파일: {}", filePath, e);
            result.setValid(false);
            result.setErrorMessage("검증 중 오류 발생: " + e.getMessage());
        }

        return result;
    }

    private AudioInfo extractAudioInfo(Path filePath) throws IOException, InterruptedException {
        Process process = getProcessForProbe(
                filePath.toAbsolutePath().toString().replace("\\", "/")
        );

        AudioInfo info = new AudioInfo();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                parseLine(line, info);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("[FFprobe] 실행 실패 (exit code: {}), 출력: {}", exitCode, output);
            throw new RuntimeException("FFprobe 실행 실패");
        }

        return info;
    }

    private void parseLine(String line, AudioInfo info) {
        if (line.startsWith("codec_name=")) {
            info.setCodec(line.substring("codec_name=".length()));
        } else if (line.startsWith("sample_rate=")) {
            try {
                info.setSampleRate(Integer.parseInt(line.substring("sample_rate=".length())));
            } catch (NumberFormatException e) {
                log.warn("샘플레이트 파싱 실패: {}", line);
            }
        } else if (line.startsWith("channels=")) {
            try {
                info.setChannels(Integer.parseInt(line.substring("channels=".length())));
            } catch (NumberFormatException e) {
                log.warn("채널 수 파싱 실패: {}", line);
            }
        } else if (line.startsWith("duration=")) {
            try {
                info.setDuration(Double.parseDouble(line.substring("duration=".length())));
            } catch (NumberFormatException e) {
                log.warn("재생시간 파싱 실패: {}", line);
            }
        } else if (line.startsWith("bit_rate=")) {
            try {
                info.setBitRate(Long.parseLong(line.substring("bit_rate=".length())));
            } catch (NumberFormatException e) {
                log.warn("비트레이트 파싱 실패: {}", line);
            }
        }
    }

    private boolean validateAudioSpecs(AudioInfo info) {
        // PCM 16비트 코덱 확인
        if (!"pcm_s16le".equals(info.getCodec())) {
            log.warn("코덱 불일치: 기대값=pcm_s16le, 실제값={}", info.getCodec());
            return false;
        }

        // 48kHz 샘플레이트 확인
        if (info.getSampleRate() != 48000) {
            log.warn("샘플레이트 불일치: 기대값=48000, 실제값={}", info.getSampleRate());
            return false;
        }

        // 스테레오(2채널) 확인
        if (info.getChannels() != 2) {
            log.warn("채널 수 불일치: 기대값=2, 실제값={}", info.getChannels());
            return false;
        }

        // 재생시간 유효성 확인
        if (info.getDuration() <= 0) {
            log.warn("재생시간 이상: {}", info.getDuration());
            return false;
        }

        return true;
    }

    private Process getProcessForProbe(String filePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_name,sample_rate,channels,duration,bit_rate",
                "-of", "default=noprint_wrappers=1",
                filePath
        );

        pb.redirectErrorStream(true);
        return pb.start();
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

    // DTO 클래스들
    @Setter
    @Getter
    public static class AudioValidationResult {
        private boolean valid;
        private String errorMessage;
        private long fileSize;
        private AudioInfo audioInfo;
    }

    @Setter
    @Getter
    public static class AudioInfo {
        private String codec;
        private int sampleRate;
        private int channels;
        private double duration;
        private long bitRate;
    }
}