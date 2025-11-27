package com.codehows.daehobe.config.SpringSecurity;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Spring Security에서 사용자 정보를 가져오는 표준 서비스 인터페이스 구현
// 인증(Authentication) 과정에서 DB에서 사용자 정보를 가져올 때 사용됨
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * @method loadUserByUsername
     * @description 주어진 사용자 이름(loginId)을 기반으로 사용자 상세 정보(UserDetails)를 로드합니다.
     * @param {String} loginId - 조회할 사용자의 이름
     * @returns {UserDetails} 로드된 사용자 상세 정보
     * @throws {UsernameNotFoundException} 해당 사용자 이름으로 사용자를 찾을 수 없는 경우
     */

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        // 1 DB에서 logijnId으로 사용자 조회
        Optional<Member> member = memberRepository.findByLoginId(loginId);

        UserDetails userDetails = null; // 최종 반환할 UserDetails 객체

        // 2 그 Member 정보를 Spring Security 내부 인증 시스템이 이해할 수 있는 UserDetails 객체(User)로 변환.
        if(member.isPresent()){
            Member user = member.get();

            // User.withUsername() : Spring Security 제공 Builder
            // - username, password, role 정보를 담아 UserDetails 생성
            // - 이 객체를 AuthenticationManager가 인증할 때 사용
            userDetails = User.withUsername(loginId)
                    .password(user.getPassword()) // DB에 저장된 암호화된 비밀번호
                    .roles(String.valueOf(user.getRole()))       // 권한 정보
                    .build();
        } else {
            // 3 DB에 유저가 존재하지 않으면 예외 발생
            // Spring Security는 이 예외를 받으면 인증 실패 처리
            throw new UsernameNotFoundException("Member not found");
        }

        // 4 최종적으로 UserDetails 반환
        // 이후 AuthenticationManager에서 비밀번호 비교 후 인증 성공 시 Authentication 객체 생성
        return userDetails;
    }
}