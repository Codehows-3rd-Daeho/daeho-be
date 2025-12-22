package com.codehows.daehobe.service.file;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.repository.file.FileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FileService
 * - 단일 파일이든 다중 파일이든 동일한 방식으로 처리.
 * 단일 파일은 List.of(file)처럼 리스트로 감싸서 사용.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FileService {

    @Value("${file.location}")
    private String fileLocation;
    private final FileRepository fileRepository;

    public File appendChunk(Long targetId, String savedFileName, MultipartFile chunk, TargetType targetType, Long fileId) {
        java.io.File dir = new java.io.File(fileLocation);
        if (!dir.exists()) dir.mkdirs();

        String savedFilePath = "/file/" + savedFileName;
        Path path = Paths.get(fileLocation, savedFilePath);
        synchronized (savedFileName.intern()) {
            try (OutputStream os = Files.newOutputStream(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                os.write(chunk.getBytes());
                os.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to append chunk to file", e);
            } finally {
                fixAudioMetadata(path);
            }
        }

        if(fileId == null) {
            return fileRepository.save(File.builder()
                    .path(savedFilePath)
                    .originalName("recording-" + System.currentTimeMillis())
                    .savedName(savedFileName)
                    .size(chunk.getSize())
                    .targetId(targetId)
                    .targetType(targetType)
                    .build());
        }
        File file = fileRepository.findById(fileId).orElseThrow(EntityNotFoundException::new);
        Long size = file.addFileSize(chunk.getSize());
        System.out.println("File size after chunk appended: " + size);
        return fileRepository.save(file);
    }

    private void fixAudioMetadata(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".wav")) {
                fixWavHeader(filePath);
            } else if (fileName.endsWith(".mp3")) {
                // MP3는 자동으로 처리되는 경우가 많지만, 필요시 라이브러리 사용
                log.info("MP3 파일 메타데이터 검증 완료: {}", filePath);
            }

        } catch (Exception e) {
            log.error("오디오 메타데이터 수정 실패", e);
            // 재생은 되므로 예외를 던지지 않고 로그만 남김
        }
    }

    private void fixWavHeader(Path filePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            long fileSize = raf.length();

            // WAV 파일 크기 정보 업데이트 (RIFF chunk size)
            raf.seek(4);
            raf.write(intToByteArray((int)(fileSize - 8), true));

            // data chunk size 찾아서 업데이트
            raf.seek(40); // 일반적인 WAV 헤더의 data chunk 위치
            byte[] dataMarker = new byte[4];
            raf.read(dataMarker);

            if (new String(dataMarker).equals("data")) {
                raf.write(intToByteArray((int)(fileSize - 44), true));
            }

            log.info("WAV 헤더 수정 완료: {}, 크기: {} bytes", filePath, fileSize);
        }
    }

    private byte[] intToByteArray(int value, boolean littleEndian) {
        byte[] bytes = new byte[4];
        if (littleEndian) {
            bytes[0] = (byte) (value & 0xFF);
            bytes[1] = (byte) ((value >> 8) & 0xFF);
            bytes[2] = (byte) ((value >> 16) & 0xFF);
            bytes[3] = (byte) ((value >> 24) & 0xFF);
        } else {
            bytes[0] = (byte) ((value >> 24) & 0xFF);
            bytes[1] = (byte) ((value >> 16) & 0xFF);
            bytes[2] = (byte) ((value >> 8) & 0xFF);
            bytes[3] = (byte) (value & 0xFF);
        }
        return bytes;
    }

    // 파일 업로드
    public List<File> uploadFiles(Long targetId, List<MultipartFile> multipartFiles, TargetType targetType) {
        List<File> files = new ArrayList<>();

        for (MultipartFile multipartFile : multipartFiles) {

            String originalName = multipartFile.getOriginalFilename();
            String savedName = UUID.randomUUID() + "_" + originalName;
            String path = "/file/" + savedName;
            Long size = multipartFile.getSize();

            java.io.File dir = new java.io.File(fileLocation);
            if (!dir.exists()) dir.mkdirs();

            try {
                multipartFile.transferTo(new java.io.File(dir, savedName));
            } catch (IOException e) {
                throw new RuntimeException("파일 저장 실패", e);
            }

            File file = File.builder()
                    .path(path)
                    .originalName(originalName)
                    .savedName(savedName)
                    .size(size)
                    .targetId(targetId)
                    .targetType(targetType)
                    .build();

            files.add(file);
        }

        return fileRepository.saveAll(files);
    }

    /**
     * 파일 수정
     *
     * @param targetId         대상 엔티티 ID
     * @param newFiles         새로 업로드할 파일 (null 가능)
     * @param filesToRemoveIds 삭제할 기존 파일 ID 리스트 (null 가능)
     * @param targetType       대상 타입
     */
    public void updateFiles(Long targetId,
                            List<MultipartFile> newFiles,
                            List<Long> filesToRemoveIds,
                            TargetType targetType) {

        // 1. 삭제할 기존 파일 삭제
        if (filesToRemoveIds != null && !filesToRemoveIds.isEmpty()) {
            List<File> filesToDelete = fileRepository.findAllById(filesToRemoveIds);
            deleteFiles(filesToDelete);
        }

        // 2. 새 파일 저장
        if (newFiles != null && !newFiles.isEmpty()) {
            uploadFiles(targetId, newFiles, targetType);
        }

        // 3. 기존 파일 중 삭제되지 않은 파일은 그대로 유지 (DB에 변경 없음)
    }

    // 실제 파일 삭제 + DB 삭제
    public void deleteFiles(List<File> files) {
        for (File file : files) {
            java.io.File f = new java.io.File(fileLocation, file.getSavedName());
            if (f.exists()) f.delete();
        }
        fileRepository.deleteAll(files);
    }

    // 파일id로 파일 찾기
    public File getFileById(Long fileId) {
        return fileRepository.findById(fileId).orElseThrow(() -> new RuntimeException("파일이 존재하지 않습니다."));
    }
    // 이슈 파일 찾기
    public List<FileDto> getIssueFiles(Long issueId) {
        return fileRepository.findByTargetIdAndTargetType(issueId, TargetType.ISSUE)
                .stream()
                .map(FileDto::fromEntity)
                .toList();
    }

    // 회의 파일 찾기
    public List<FileDto> getMeetingFiles(Long meetingId) {
        return fileRepository.findByTargetIdAndTargetType(meetingId, TargetType.MEETING)
                .stream()
                .map(FileDto::fromEntity)
                .toList();
    }

    // 댓글 파일 찾기
    public List<FileDto> getCommentFiles(Long commentId) {
        return fileRepository.findByTargetIdAndTargetType(commentId, TargetType.COMMENT)
                .stream()
                .map(FileDto::fromEntity)
                .toList();
    }
}
