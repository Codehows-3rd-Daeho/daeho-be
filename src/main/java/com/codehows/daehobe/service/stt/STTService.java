package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.entity.file.STT;
import com.codehows.daehobe.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class STTService {
    private final FileService fileService;

    public List<STTDto> uploadSTT(Long id, List<MultipartFile> files){

        List<STT> stts = new ArrayList<>();

        //1. stt 엔티티에 빈객체 저장

        //2. stt id로 fileService => 음성 파일 저장
        fileService.uploadFiles(id, files, TargetType.STT);//targetId사용해야함

        //3. stt api 호출
        //4. 반환값 List<STT>  entity에 update
        //5.  반환

        return null;
    }
}
