package com.codehows.daehobe.config.SpringSecurity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 유효한 자격 증명이 없는 상태에서 보호된 리소스에 접근할 때
        // HTTP 상태 코드 401 (Unauthorized)와 함께 "Unauthorized" 메시지를 응답으로 보냅니다.
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
