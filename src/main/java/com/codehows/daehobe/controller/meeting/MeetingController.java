package com.codehows.daehobe.controller.meeting;

import com.codehows.daehobe.dto.issue.IssueDto;
import com.codehows.daehobe.dto.meeting.MeetingDto;
import com.codehows.daehobe.service.meeting.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    //ResponseEntity: HTTP 상태 코드, 헤더 등을 함께 설정 가능하게 하는 wrapper(다른 객체나 값을 감싸는 객체)
    //<T>: 보낼 데이터
    @PostMapping("/create")
    public ResponseEntity<?> createMeeting(
            @RequestPart("data") MeetingDto meetingDto ,
            @RequestPart(value = "file", required = false) List<MultipartFile> multipartFiles){

        System.out.println("==============이슈 등록 시작");
        meetingService.createMeeting(meetingDto, multipartFiles);

        return ResponseEntity.ok().build();

    }

}
