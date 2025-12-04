package com.codehows.daehobe.config.SpringSecurity;

import com.codehows.daehobe.config.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
   스프링 시큐리티 보안 필터 체인(SecurityFilterChain)을 정의한 것
   세션 대신 JWT로만 인증 관리하게 하고, 로그인(/login)만 열어두고, 나머진 인증된 자만 접근 허락하는 구조
*/

@Configuration // 스프링 설정 클래스
// 이 클래스 안에 작성된 메서드들 중 @Bean이 붙은 메서드의 반환값을 Spring 컨테이너가 Bean으로 등록한다는 뜻
@EnableWebSecurity // Spring Security 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter; // JWT 토큰 생성 및 검증을 담당하는 제공자
    private final AuthEntryPoint authEntryPoint; // JWT 인증 실패 시 401 응답을 처리

    @Bean
    // Spring Security에서 보안 필터 체인을 수동 설정. HttpSecurity를 이용해서 요청 URL에 대한 보안 정책을 설정
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF 보호 기능 끄기 (JWT 기반 인증에선 세션을 쓰지 않으므로 필요 없음)
        http.csrf(csrf -> csrf.disable())

                // 세션을 아예 만들지 않도록 설정 (STATELESS → 모든 요청은 JWT 토큰 기반으로 인증)
                .sessionManagement((session) -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인증되지 않은 사용자가 보호된 리소스에 접근할 때 JwtAuthenticationEntryPoint를 호출하여 401 Unauthorized 응답을 반환.
                .exceptionHandling((ex) -> ex.authenticationEntryPoint(authEntryPoint))

                // 요청 URL 별 접근 권한 설정
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/login","/file/**").permitAll()
                        .requestMatchers( "/index.html",  "/sw.js", "/manifest.webmanifest").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN") //admin으로 시작하는 경로는 admin role일 경우에만 접근 가능하도록.

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated())

                // 로그인 필터보다 먼저 모든 요청을 가로채서 JWT 토큰을 확인하고 인증 정보를 SecurityContext에 넣겠다
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);


        // 최종적으로 보안 필터 체인 객체 반환
        return http.build();
    }


    /*
    PasswordEncoder : Spring Security에서 비밀번호를 암호화하고 검증하는 인터페이스
    NoOpPasswordEncoder : 암호화를 전혀 하지 않는 방식
    즉, DB에 저장된 비밀번호와 사용자가 입력한 비밀번호를 그대로 비교
    {noop}1234처럼 비밀번호 앞에 {noop}을 붙여서 사용
    */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 해시 함수를 이용하여 암호화하여 저장.
        // 단방향 암호화.(복호화 불가능). 로그인할때마다 hash함수로 유사성 비교.(동일x)
    }

    //스프링 시큐리티에서 기본으로 설정된 인증 매니저(AuthenticationManager)를 Bean으로 등록해두는 메서드
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        // JWT 로그인 시, 아이디/비밀번호 검증을 직접 수행할 때 필요.
        // => 로그인 성공 시 Authentication 반환 → 이 정보로 JWT 발급
        return authConfig.getAuthenticationManager();
    }
}
