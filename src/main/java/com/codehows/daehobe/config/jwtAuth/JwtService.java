package com.codehows.daehobe.config.jwtAuth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT(JSON Web Token) 생성, 파싱 및 검증을 담당하는 서비스 클래스입니다.
 */
@Service
public class JwtService {
    /**
     * HTTP Authorization 헤더에 사용될 접두사입니다. (e.g., "Bearer {token}")
     */
    static final String PREFIX = "Bearer ";
    /**
     * JWT 토큰의 만료 시간 (2시간)
     */
   static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000;

    @Value("${jwt.key}")
    private String jwtKey;

    private Key signingKey;

    /**
     * 서비스 초기화 시 `jwt.key` 프로퍼티 값을 사용하여 서명 키를 생성합니다.
     * ` @PostConstruct` 어노테이션을 통해 의존성 주입 후 한 번만 실행됩니다.
     */
    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 사용자 ID(memberId)와 역할(role)을 기반으로 JWT 토큰을 생성합니다.
     *
     * @param memberId 사용자의 고유 ID
     * @param role     사용자의 권한 (e.g., "USER", "ADMIN")
     * @return 생성된 JWT 문자열
     */
    public String generateToken(String memberId, String role) {
        return Jwts.builder()
                .setSubject(memberId)
                .claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * HttpServletRequest의 Authorization 헤더에서 JWT 토큰을 파싱하여 사용자 ID와 역할을 추출합니다.
     *
     * @param request HTTP 요청 객체
     * @return 토큰이 유효한 경우 사용자 ID("memberId")와 역할("role")이 담긴 Map, 그렇지 않으면 null
     */
    public Map<String, String> parseTokenWithRole(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.replace(PREFIX, "");
            try {
                var claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String memberId = claims.getSubject();
                String role = claims.get("role", String.class);

                if (memberId != null && role != null) {
                    return Map.of(
                            "memberId", memberId,
                            "role", role
                    );
                }
            } catch (Exception e) {
                // 토큰 파싱/검증 실패 시 (e.g., 만료, 서명 불일치) null을 반환하여 인증 실패를 유도합니다.
                return null;
            }
        }

        return null;
    }

    /**
     * JWT 토큰 문자열을 파싱하여 사용자 ID와 역할을 추출합니다. (WebSocket 인증용)
     *
     * @param token JWT 토큰 문자열
     * @return 토큰이 유효한 경우 사용자 ID("memberId")와 역할("role")이 담긴 Map, 그렇지 않으면 null
     */
    public Map<String, String> parseTokenWithRole(String token) {
        if (token != null) {
            try {
                var claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String memberId = claims.getSubject();
                String role = claims.get("role", String.class);

                if (memberId != null && role != null) {
                    return Map.of(
                            "memberId", memberId,
                            "role", role
                    );
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}