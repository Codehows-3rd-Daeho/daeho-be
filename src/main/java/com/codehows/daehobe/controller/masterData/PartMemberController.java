package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.PartMemberDto;
import com.codehows.daehobe.dto.masterData.PartMemberListDto;
import com.codehows.daehobe.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/partMember")
@RequiredArgsConstructor
public class PartMemberController {

    private final MemberService memberService;


    //PartmemberController에서 멤버 컨트롤러로 이동
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

    //주관자 조회 삭제


}
