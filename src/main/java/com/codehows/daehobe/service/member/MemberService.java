package com.codehows.daehobe.service.member;

import com.codehows.daehobe.dto.MemberDto;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {
    public void createMember(@Valid MemberDto memberDto) {
    }
}
