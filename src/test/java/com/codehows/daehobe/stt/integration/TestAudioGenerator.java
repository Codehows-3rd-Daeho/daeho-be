package com.codehows.daehobe.stt.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * 테스트용 오디오 파일 생성 유틸리티
 */
public class TestAudioGenerator {

    private static final Random random = new Random();

    /**
     * WAV 형식의 테스트 오디오 데이터 생성
     *
     * @param durationMs 오디오 길이 (밀리초)
     * @param sampleRate 샘플레이트 (기본 16000Hz)
     * @return WAV 파일 바이트 배열
     */
    public static byte[] generateWavAudio(int durationMs, int sampleRate) {
        int channels = 1; // 모노
        int bitsPerSample = 16;
        int bytesPerSample = bitsPerSample / 8;
        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        int dataSize = numSamples * channels * bytesPerSample;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // RIFF header
            baos.write("RIFF".getBytes());
            baos.write(intToBytes(36 + dataSize, 4)); // File size - 8
            baos.write("WAVE".getBytes());

            // fmt subchunk
            baos.write("fmt ".getBytes());
            baos.write(intToBytes(16, 4)); // Subchunk1 size (16 for PCM)
            baos.write(intToBytes(1, 2)); // Audio format (1 = PCM)
            baos.write(intToBytes(channels, 2)); // Number of channels
            baos.write(intToBytes(sampleRate, 4)); // Sample rate
            baos.write(intToBytes(sampleRate * channels * bytesPerSample, 4)); // Byte rate
            baos.write(intToBytes(channels * bytesPerSample, 2)); // Block align
            baos.write(intToBytes(bitsPerSample, 2)); // Bits per sample

            // data subchunk
            baos.write("data".getBytes());
            baos.write(intToBytes(dataSize, 4)); // Data size

            // Generate random audio samples (white noise)
            byte[] audioData = new byte[dataSize];
            random.nextBytes(audioData);
            baos.write(audioData);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate WAV audio", e);
        }
    }

    /**
     * 기본 설정으로 테스트 오디오 청크 생성
     *
     * @return 1초 길이의 WAV 오디오 데이터
     */
    public static byte[] generateTestChunk() {
        return generateWavAudio(1000, 16000);
    }

    /**
     * 지정된 크기의 테스트 오디오 청크 생성
     *
     * @param sizeBytes 대략적인 파일 크기 (바이트)
     * @return WAV 오디오 데이터
     */
    public static byte[] generateTestChunk(int sizeBytes) {
        // WAV 헤더가 44바이트이므로 데이터 크기 계산
        int dataSize = Math.max(sizeBytes - 44, 100);
        // 16비트 모노, 16kHz 기준으로 시간 계산
        int durationMs = (dataSize / 32) + 1; // 32 bytes per ms at 16kHz, 16-bit mono
        return generateWavAudio(durationMs, 16000);
    }

    /**
     * 사인파 오디오 생성 (테스트용 실제 톤)
     *
     * @param durationMs    오디오 길이 (밀리초)
     * @param frequencyHz   주파수 (Hz)
     * @param sampleRate    샘플레이트
     * @return WAV 파일 바이트 배열
     */
    public static byte[] generateSineWaveAudio(int durationMs, int frequencyHz, int sampleRate) {
        int channels = 1;
        int bitsPerSample = 16;
        int bytesPerSample = bitsPerSample / 8;
        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        int dataSize = numSamples * channels * bytesPerSample;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // RIFF header
            baos.write("RIFF".getBytes());
            baos.write(intToBytes(36 + dataSize, 4));
            baos.write("WAVE".getBytes());

            // fmt subchunk
            baos.write("fmt ".getBytes());
            baos.write(intToBytes(16, 4));
            baos.write(intToBytes(1, 2));
            baos.write(intToBytes(channels, 2));
            baos.write(intToBytes(sampleRate, 4));
            baos.write(intToBytes(sampleRate * channels * bytesPerSample, 4));
            baos.write(intToBytes(channels * bytesPerSample, 2));
            baos.write(intToBytes(bitsPerSample, 2));

            // data subchunk
            baos.write("data".getBytes());
            baos.write(intToBytes(dataSize, 4));

            // Generate sine wave samples
            ByteBuffer buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / sampleRate;
                double sample = Math.sin(2 * Math.PI * frequencyHz * t);
                short sampleValue = (short) (sample * Short.MAX_VALUE * 0.8); // 80% amplitude
                buffer.putShort(sampleValue);
            }

            baos.write(buffer.array());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate sine wave audio", e);
        }
    }

    private static byte[] intToBytes(int value, int numBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(numBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (numBytes == 2) {
            buffer.putShort((short) value);
        } else {
            buffer.putInt(value);
        }
        return buffer.array();
    }
}
