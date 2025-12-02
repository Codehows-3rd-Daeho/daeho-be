package com.codehows.daehobe.controller.member;

import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.service.member.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // 목록 조회
    @GetMapping("/admin/member")
    public ResponseEntity<?> getMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
            Page<MemberListDto> dtoList = memberService.findAll(pageable);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 조회 중 오류 발생");
        }
    }

    // 상세 조회
    @GetMapping("/admin/member/{id}")
    public ResponseEntity<?> getMemberDtl(@PathVariable Long id) {
        try {
            MemberDto dtoList = memberService.getMemberDtl(id);
            return ResponseEntity.ok(dtoList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 조회 중 오류 발생");
        }
    }

    // 비밀번호 초기화 및 임시 비밀번호 생성.
    @PostMapping("/admin/member/{id}/generatePwd")
    public ResponseEntity<?> generatePwd(@PathVariable Long id) {
        try {
            String newPassword = memberService.generatePwd(id);
            return ResponseEntity.ok(
                    Map.of(
                            "message", "비밀번호가 성공적으로 초기화되었습니다.",
                            "newPassword", newPassword
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("비밀번호 초기화 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/admin/member/{id}")
    public ResponseEntity<?> updateMember(@PathVariable Long id, @RequestPart("data") @Valid MemberDto memberDto,
                                          @RequestPart(value = "file", required = false) List<MultipartFile> profileImage) {
        try {
            memberService.updateMember(id, memberDto, profileImage);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 수정 중 오류 발생");
        }
    }
}