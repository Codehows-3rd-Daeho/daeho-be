package com.codehows.daehobe.config.jpaAuditor;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

// JPA가 @CreatedBy나 @LastModifiedBy 값을 넣어줄 때 "누가" 했는지를 여기서 가져감
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<Long> {
    /*
    Entity 생성 및 수정 시에 해당 행위의 주체(유저)의 정보를 알아내는 역할
    구현하려면 : sequrity context 에 Authentication을 꺼내면 user정보가 있음. => loginId.
   */
    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        String memberId = authentication.getName();

        if (memberId == null) {
            return Optional.empty();
        }

        // 2. 영속성 컨텍스트 Proxy 반환
        return Optional.of(Long.valueOf(memberId));
    }

}