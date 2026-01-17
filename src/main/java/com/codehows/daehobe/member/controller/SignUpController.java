package com.codehows.daehobe.member.controller;

import com.codehows.daehobe.member.dto.MemberDto;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.codehows.daehobe.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignUpController {
    private final MemberService memberService;
    private final MemberRepository memberRepository;

    // 회원가입
    @PostMapping
    public ResponseEntity<?> signUp(@RequestPart("data") @Valid MemberDto memberDto,
                                    @RequestPart(value = "file", required = false) List<MultipartFile> profileImage) {
        try {
            Member member = memberService.createMember(memberDto, profileImage);
            return ResponseEntity.ok(member);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 생성 중 오류 발생");
        }
    }

    // 아이디 중복 확인
    @GetMapping("/check_loginId")
    public ResponseEntity<?> checkLoginId(@RequestParam String loginId) {
        boolean exists = memberRepository.existsByLoginId(loginId);
        return ResponseEntity.ok().body(Map.of("exists", exists));
    }

}