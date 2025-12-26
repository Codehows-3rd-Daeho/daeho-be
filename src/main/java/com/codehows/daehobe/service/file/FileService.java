package com.codehows.daehobe.service.file;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.utils.AudioProcessingService;
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
import java.nio.file.*;
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
    private final AudioProcessingService audioProcessingService;

    public File createFile(String fileName, Long targetId, TargetType targetType) {
        String filePath = "/file/" + fileName;
        return fileRepository.save(File.builder()
                .path(filePath)
                .originalName(fileName)
                .savedName(fileName)
                .size(0L)
                .targetId(targetId)
                .targetType(targetType)
                .build());
    }

    public void appendChunk(Long targetId, MultipartFile chunk, TargetType targetType) {
        java.io.File dir = new java.io.File(fileLocation);
        if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("Unable to create directory: " + fileLocation);

        List<File> recordingFiles = fileRepository.findByTargetIdAndTargetType(targetId, targetType);
        if(recordingFiles.isEmpty()) {
            throw new EntityNotFoundException("File not found");
        }
        File recordingFile = recordingFiles.getFirst();
        Path path = Paths.get(fileLocation, recordingFile.getSavedName());
        synchronized (recordingFile.getSavedName().intern()) {
            try (OutputStream os = Files.newOutputStream(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                os.write(chunk.getBytes());
                os.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to append chunk to file", e);
            }
        }
        Long size = recordingFile.addFileSize(chunk.getSize());
        System.out.println("File size after chunk appended: " + size);
        fileRepository.save(recordingFile);
    }

    public File encodeAudioFile(Long targetId, TargetType targetType) {
        List<File> recordingFiles = fileRepository.findByTargetIdAndTargetType(targetId, targetType);
        if(recordingFiles.isEmpty()) {
            throw new EntityNotFoundException("File not found");
        }
        File recordingFile = recordingFiles.getFirst();
        Path path = Paths.get(fileLocation, recordingFile.getSavedName());
        audioProcessingService.fixAudioMetadata(path);
        return recordingFile;
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

    public List<File> getSTTFiles(List<Long> sttIds) {
        return fileRepository.findByTargetIdInAndTargetType(sttIds, TargetType.STT);
    }
}
