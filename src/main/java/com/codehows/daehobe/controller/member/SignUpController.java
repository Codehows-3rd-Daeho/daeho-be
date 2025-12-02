package com.codehows.daehobe.controller.member;

import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignUpController {
    private final MemberService memberService;
    private final MemberRepository memberRepository;

    // 회원가입
    @PostMapping
    public ResponseEntity<?> signUp(@RequestBody @Valid MemberDto memberDto) {
        try {
            Member member = memberService.createMember(memberDto);
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