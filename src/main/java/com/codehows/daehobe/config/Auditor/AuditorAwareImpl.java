package com.codehows.daehobe.config.Auditor;

import com.codehows.daehobe.entity.Member;
import com.codehows.daehobe.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

// JPA가 @CreatedBy나 @LastModifiedBy 값을 넣어줄 때 "누가" 했는지를 여기서 가져감

@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<Member> {

    private final MemberRepository memberRepository;

    /*
    Entity 생성 및 수정 시에 해당 행위의 주체(유저)의 정보를 알아내는 역할
    구현하려면 : sequrity context 에 Authentication을 꺼내면 user정보가 있음. => loginId.
   */
    @Override
    public Optional<Member> getCurrentAuditor() {
        // 현재 스레드의 SecurityContext에서 인증 정보를 가져옴
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 인증 정보가 없거나, 인증되지 않았거나, 익명 사용자인 경우
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty(); // Auditor를 반환할 수 없음
        }

        String loginId;
        // 인증 객체에서 principal을 꺼냄
        Object principal = authentication.getPrincipal();
        // principal이 단순 문자열이면 loginId로 사용
        if (principal instanceof String) {
            loginId = (String) principal;
        }
        // principal이 Spring Security의 UserDetails라면 username(loginId)을 가져옴
        else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            loginId = userDetails.getUsername();
        }
        // 그 외 타입이면 Auditor 정보를 제공할 수 없음
        else {
            return Optional.empty();
        }

        // loginId 기반으로 DB 조회
        return memberRepository.findByLoginId(loginId);
    }

}
