package com.codehows.daehobe.controller.member;

import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/admin/member")
    public ResponseEntity<?> getMembers(){
        try {
            List<MemberListDto> dtoList = memberService.findAll();
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 조회 중 오류 발생");
        }
    }
}
