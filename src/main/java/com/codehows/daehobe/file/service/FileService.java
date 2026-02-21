package com.codehows.daehobe.file.service;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.dto.FileDto;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.repository.FileRepository;
import com.codehows.daehobe.common.utils.AudioProcessor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final AudioProcessor audioProcessor;

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

    public File appendChunk(Long targetId, MultipartFile chunk, TargetType targetType) {
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
        return recordingFile;
    }

    public File encodeAudioFile(File originalFile) {
        Path originalPath = Paths.get(fileLocation, originalFile.getSavedName());
        String newSavedName = "encoded-" + UUID.randomUUID() + ".wav";
        Path newPath = Paths.get(fileLocation, newSavedName);

        synchronized (originalFile.getSavedName().intern()) {
            audioProcessor.fixAudioMetadata(originalPath, newPath);
        }

        try {
            long newSize = Files.size(newPath);
            File newFile = File.builder()
                    .path("/file/" + newSavedName)
                    .originalName(originalFile.getOriginalName())
                    .savedName(newSavedName)
                    .size(newSize)
                    .targetId(originalFile.getTargetId())
                    .targetType(originalFile.getTargetType())
                    .build();
            
            fileRepository.save(newFile);
            deleteFiles(List.of(originalFile));

            return newFile;
        } catch (IOException e) {
            // 새 파일 생성 실패 시 롤백 (생성된 새 파일 삭제)
            try {
                Files.deleteIfExists(newPath);
            } catch (IOException ex) {
                log.error("Failed to delete temporary encoded file: {}", newSavedName, ex);
            }
            throw new RuntimeException("Failed to create encoded file record", e);
        }
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

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    );

    public String storeEmbedImage(MultipartFile file) throws IOException {
        // 파일명 검증
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());

        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("파일명에 부적절한 경로가 포함되어 있습니다: " + originalFileName);
        }

        // 파일 확장자 검증
        int lastDotIndex = originalFileName.lastIndexOf('.');
        String fileExtension = lastDotIndex > 0 ? originalFileName.substring(lastDotIndex).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + fileExtension);
        }

        // 고유한 파일명 생성 (UUID + 원본 확장자)
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

        // 파일 저장
        Path targetLocation = Paths.get(fileLocation).toAbsolutePath().normalize().resolve(uniqueFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        }

        return uniqueFileName;
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

    public File getSTTFile(Long sttId) {
        return fileRepository.findByTargetIdAndTargetType(sttId, TargetType.STT).getFirst();
    }

    public List<File> getSTTFiles(List<Long> sttIds) {
        return fileRepository.findByTargetIdInAndTargetType(sttIds, TargetType.STT);
    }
  
    //멤버 프로필 찾기
    public File findFirstByTargetIdAndTargetType(Long id, TargetType targetType) {
        return fileRepository.findFirstByTargetIdAndTargetType(id, targetType)
                .orElse(null);
    }
}
