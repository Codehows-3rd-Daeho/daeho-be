package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.PartMemberDto;
import com.codehows.daehobe.dto.masterData.PartMemberListDto;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/partMember")
@RequiredArgsConstructor
public class PartMemberController {

    private final MemberService memberService;

    @GetMapping("/list")
    public ResponseEntity<?> getPartMemberList() {
        System.out.println("참여자 컨트롤러 작동 확인");
        try {
            List<PartMemberListDto> partMembers = memberService.findAll();
            return ResponseEntity.ok(partMembers);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 조회 중 오류 발생");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getHost(@PathVariable Long id) {
        try {
            PartMemberDto partMember = memberService.findHostById(id);
            System.out.println("주관자 컨트롤러 작동 확인");
            return ResponseEntity.ok(partMember);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("주관자 조회 중 오류 발생");
        }
    }

}
