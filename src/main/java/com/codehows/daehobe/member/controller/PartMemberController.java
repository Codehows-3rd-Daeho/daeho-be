package com.codehows.daehobe.member.controller;

import com.codehows.daehobe.member.dto.PartMemberListDto;
import com.codehows.daehobe.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/partMember")
@RequiredArgsConstructor
public class PartMemberController {

    private final MemberService memberService;


    @GetMapping("/list")
    public ResponseEntity<?> getPartMemberList() {

        //user, 재직중
        System.out.println("참여자 컨트롤러 작동 확인");
        try {
            List<PartMemberListDto> partMembers = memberService.findByRoleAndIsEmployedTrue();
            return ResponseEntity.ok(partMembers);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 조회 중 오류 발생");
        }
    }
}
