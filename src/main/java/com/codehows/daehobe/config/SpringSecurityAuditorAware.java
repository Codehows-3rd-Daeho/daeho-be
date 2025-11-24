package com.codehows.daehobe.config;

import com.codehows.daehobe.entity.Member;
import com.codehows.daehobe.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SpringSecurityAuditorAware implements AuditorAware<Member> {

    private final MemberRepository memberRepository;

    @Override
    public Optional<Member> getCurrentAuditor() {
        // Spring Security에서 인증된 사용자 ID 가져오기
        // 실제 프로젝트에서는 SecurityContextHolder에서 username 가져와서 조회
        String currentUsername = getCurrentUsernameFromSecurityContext();
        return memberRepository.findByLoginId(currentUsername);
    }
    private String getCurrentUsernameFromSecurityContext() {
        // SecurityContextHolder 활용, 로그인 유저 가져오는 로직 구현
        // return SecurityContextHolder.getContext().getAuthentication().getName();
        return "system"; // 예시: 로그인 없는 환경에서 "system" 처리
    }
}
