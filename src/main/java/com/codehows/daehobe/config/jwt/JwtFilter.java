package com.codehows.daehobe.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @class JwtFilter
 * @description 모든 HTTP 요청에 대해 한 번만 실행되는 필터로, JWT 토큰을 검증하고
 * 인증된 사용자의 정보를 Spring Security 컨텍스트에 설정합니다.
 * `OncePerRequestFilter`를 상속받아 요청당 한 번의 필터 실행을 보장합니다.
 */

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    /**
     * @param {HttpServletRequest}  request - 현재 HTTP 요청 객체
     * @param {HttpServletResponse} response - 현재 HTTP 응답 객체
     * @param {FilterChain}         filterChain - 다음 필터 또는 서블릿으로 요청을 전달하는 객체
     * @throws {ServletException} 서블릿 관련 예외 발생 시
     * @throws {IOException}      입출력 관련 예외 발생 시
     * @method doFilterInternal
     * @description 실제 필터링 로직을 수행하는 메서드.
     * 요청 헤더에서 JWT 토큰을 추출하고 유효성을 검사하여 인증 정보를 설정.
     * 1. parseTokenWithRole로 유저 정보 추출 후 Authentication 객체 생성
     * 2. SecurityContext에 설정
     */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 필터 ==> 요청, 응답을 중간에서 가로챈 다음 ==> 필요한 동작을 수행
        // 1. 요청 헤더 (Authorization)에서 JWT 토큰을 꺼냄
        String jwtToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (jwtToken != null) {
            // 2. 꺼낸 토큰에서 유저 정보 추출
            Map<String, String> userInfo = jwtService.parseTokenWithRole(request);
            String loginId = userInfo.get("loginId");
            String role = userInfo.get("role");

            // role을 Spring Security 권한으로 변환
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

            // 3. 추출된 유저 정보로 Authentication 객체 생성. SecurityContext에 설정
            //principal → loginId (사용자 ID) - “누구인가?”Spring Security에서 인증 객체의 주체(사용자) 를 뜻함.
            //credentials → null (이미 JWT로 인증이 끝난 상태라 비밀번호 불필요)
            //authorities → 권한
            if (loginId != null) {
                // SecurityContext에 인증 객체 저장 UsernamePasswordAuthenticationToken(Principal, Credentials, 권한목록)
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        loginId,
                        null, //JWT 인증 후에는 이미 인증이 끝난 상태라, 더 이상 비밀번호가 필요 없기 때문
                        authorities
                );

                // SecurityContextHolder는 현재 스레드의 보안 컨텍스트를 저장하는 역할을 합니다.
                // 생성된 인증 객체를 Security Context에 저장. 이제 이 요청은 인증된 사용자의 요청으로 간주됨.
                // 서버는 JWT를 검증하고 SecurityContext에 인증 정보를 세팅함으로써 @AuthenticationPrincipal 혹은 SecurityContextHolder로 사용자 정보 접근 가능
                SecurityContextHolder.getContext().setAuthentication(authentication);

                System.out.println("Authentication object created: " + authentication);
                System.out.println("LoginId: " + loginId + ", Role: " + role);
                System.out.println("SecurityContext set: " + SecurityContextHolder.getContext().getAuthentication());

            }
        }
        // 다음 필터로 요청을 전달
        filterChain.doFilter(request, response);
    }
}
