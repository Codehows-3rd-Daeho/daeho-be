package com.codehows.daehobe.config.jwt;

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

@Service
public class JwtService {
    // 서버와 클라이언트가 주고 받는 토근 ==> HTTP Header 내 Authorization 헤더값에 저장
    // 예) Authorization Bearer <토큰값>
    static final String PREFIX = "Bearer ";
//        static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;  // 86,400,000 시간 => 하루
   static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000; // 2시간
    // JWT 서명에 사용할 비밀키 (HS256 알고리즘 기반으로 랜덤 생성)
    static final Key SIGNING_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    @Value("${jwt.key}")
    private String jwtKey;

    private Key signingKey;

    // 서버 시작 시 한 번만 실행
    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtKey.getBytes(StandardCharsets.UTF_8));
    }

    // loginId(ID)를 받아서 JWT 생성
    public String generateToken(String memberId, String role) {
        return Jwts.builder()
                // 토큰의 주제를 loginId 지정
                .setSubject(memberId)
                // USER or ADMIN
                .claim("role", role)
                // 만료 시간 설정 (현재시간 + 유효시간)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                // 비밀키로 서명 (HS256방식)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                // 최종적으로 compact()를 호출해 문자열 형태의 토큰 생성
                .compact();
    }

    //  요청의 Authorization 헤더에서 토큰을 가져온뒤 토큰을 확인하고 loginId(ID)를 반환
    public Map<String, String> parseTokenWithRole(HttpServletRequest request) {
        // 요청 헤더에서 Authorization 헤더값을 가져옴   예) header = Bearer <토큰값>
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // 헤더가 존재하고 "Bearer "로 시작하면
        if (header != null && header.startsWith(PREFIX)) {
            // "Bearer " 접두어를 제거하고 순수 토큰만 남김
            String token = header.replace(PREFIX, "");
            var claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String memberId = claims.getSubject();
            String role = claims.get("role", String.class);

            // 아이디가 존재하면 반환
            if (memberId != null && role != null) {
                return Map.of(
                        "memberId", memberId,
                        "role", role
                );
            }
        }

        // 토큰이 없거나 유효하지 않은 경우 null 반환
        return null;
    }

}