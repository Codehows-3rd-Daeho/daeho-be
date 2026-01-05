//package com.codehows.daehobe.controller.comment;
//
//import com.codehows.daehobe.dto.comment.MentionMemberDto;
//import com.codehows.daehobe.service.member.MemberService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequiredArgsConstructor
//public class MentionController {
//
//    private final MemberService memberService;
//
//    // 멘션 회원 조회
//    @GetMapping("/members/search")
//    public List<MentionMemberDto> searchMembers(
//            @RequestParam String keyword
//    ) {
//        return memberService.searchForMention(keyword);
//    }
//
//}
