package com.codehows.daehobe.config.SpringSecurity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @class AuthEntryPoint
 * @description Spring Security 필터 체인에서 인증되지 않은 사용자가 보호된 리소스에 접근하려고 할 때 호출되는
 *              커스텀 `AuthenticationEntryPoint` 구현체입니다.
 *              JWT 토큰 인증 실패 시 HTTP 401 Unauthorized 응답을 클라이언트에 반환합니다.
 */
//인증 (401)
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 유효한 자격 증명이 없는 상태에서 보호된 리소스에 접근할 때
        // HTTP 상태 코드 401 (Unauthorized)와 함께 "Unauthorized" 메시지를 응답으로 보냅니다.
//        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        //UserDetailsServiceImpl의 진짜 예외
        //Throwable: 던질 수 있는 모든 오류와 예외의 최상위 클래스
        Throwable cause = authException.getCause();

        String message;

        if (cause instanceof DisabledException) {
            message = "퇴사 처리된 계정입니다.";
        } else if (authException instanceof BadCredentialsException) {
            message = "아이디 또는 비밀번호가 올바르지 않습니다.";
        } else {
            message = "아이디 또는 비밀번호가 올바르지 않습니다.";
        }


        response.getWriter().write("""
        {
          "message": "%s"
        }
        """.formatted(message));


    }
}
