package com.codehows.daehobe.service.file;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.repository.file.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${fileLocation}")
    private String fileLocation;
    private final FileRepository fileRepository;

    //파일 업로드
//    MultipartFile List를 받아 각 파일별로 originalName savedName path size targetType를 저장한다
//    각 파일을 FileDto List에 담고 Entity로 변환 후 Repository에 저장한다.
    public List<FileDto> uploadFiles( Long issueId,  List<MultipartFile> multipartFiles) {

        //FileDto 사용: 파일 저장 후 프론트나 다른 서비스 계층에서 파일 정보를 사용할 목적
        List<FileDto> files = new ArrayList<>();

        for (MultipartFile file : multipartFiles) {
            //MultipartFile 인터페이스에서 제공하는 원본파일 이름을 가져오는 메서드
            String originalName = file.getOriginalFilename();
            //Java 기본 API
            String savedName  = UUID.randomUUID() + "_" + originalName;
            //path: 최종 저장 경로
            String path = fileLocation + "/" + savedName;
            //MultipartFile 인터페이스에서 제공하는 원본파일 크기를 가져온는 메서드
            Long size = file.getSize();
            TargetType targetType = TargetType.ISSUE;


            FileDto saveFile = FileDto.builder()
                    .path(path)
                    .originalName(originalName)
                    .savedName(savedName)
                    .size(size)
                    .targetId(issueId)
                    .targetType(targetType)
                    .build();
            files.add(saveFile);

        }

        //Dto -> Entity 변환 후 리스트 저장
        List<File> finalFile = files.stream()//stream: 처음 연산(각 요소를 하나씩 처리 가능), map: 중간연산, collect: 최종 연산
                .map(FileDto::toEntity) // DTO 내부 toEntity() 호출 === fileDto -> fileDto.toEntity()
                .collect(Collectors.toList()); //변환된 File 객체들을 List<File>로 모음

        fileRepository.saveAll(finalFile);
        return files;
    }
}
