package com.codehows.daehobe.controller.member;

import com.codehows.daehobe.dto.MemberDto;
import com.codehows.daehobe.repository.MemberRepository;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignUpController {
    private final MemberService memberService;
    private final MemberRepository memberRepository;

    @PostMapping
    public ResponseEntity<?> signUp(@RequestBody @Valid MemberDto memberDto) {
        try {
            memberService.createMember(memberDto);
            return ResponseEntity.ok("회원가입 성공");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/check_loginId")
    public ResponseEntity<?> checkLoginId(@RequestParam String loginId) {
        boolean exists = memberRepository.existsByLoginId(loginId);
        return ResponseEntity.ok().body(Map.of("exists", exists));
    }
}
