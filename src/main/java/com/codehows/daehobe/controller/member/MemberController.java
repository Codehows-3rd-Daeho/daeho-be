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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
    public ResponseEntity<?> updateMember(
            @PathVariable Long id,
            @RequestPart("data") @Valid MemberDto memberDto,
            @RequestPart(value = "file", required = false) MultipartFile profileImage,
                @RequestPart(value = "removeFileIds", required = false) String removeFileId // 삭제할 파일 ID (없으면 null)
    ) {
        try {
            // 단일 파일도 리스트로 감싸서 FileService 사용
            List<MultipartFile> newFiles = profileImage != null ? List.of(profileImage) : List.of();
            // 삭제 파일 id도 Long으로 변환 후 리스트로 감싸서 전달

            /** 삭제할 파일 ID 처리
             * 1. 삭제할 파일이 있는 경우 : removeFileId != null. 문자열 ID → Long 변환 후 리스트([id]) 생성. 서비스에서 해당 파일 삭제
             * 2. 삭제할 파일이 없는 경우 : removeFileId == null. 빈 리스트([]) 생성. 서비스에서 삭제 로직 무시
             * */
            List<Long> removeFileIds;
            if (removeFileId != null) {
                // JSON 배열 문자열을 List<Long>로 변환
                ObjectMapper objectMapper = new ObjectMapper(); //JSON 문자열을 Java 리스트(List<Long>)로 변환
                removeFileIds = objectMapper.readValue(removeFileId, new TypeReference<List<Long>>() {});
            } else {
                removeFileIds = List.of();
            }
            memberService.updateMember(id, memberDto, newFiles, removeFileIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("회원 수정 중 오류 발생");
        }
    }

}