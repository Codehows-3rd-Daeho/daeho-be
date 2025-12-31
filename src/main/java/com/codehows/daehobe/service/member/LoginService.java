package com.codehows.daehobe.service.member;

import com.codehows.daehobe.config.jwt.JwtService;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.member.LoginDto;
import com.codehows.daehobe.dto.member.LoginResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MemberRepository memberRepository;
    private final FileService fileService;

    /**
     * 로그인 처리 후 JWT 발급
     *
     * @param loginDto 로그인 요청 DTO
     * @return 발급된 JWT 토큰
     */
    public LoginResponseDto login(LoginDto loginDto) {

        // 1. user의 id pw 정보를 기반으로 UsernamePasswordAuthenticationToken을 생성 (인증용 토큰)
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        loginDto.getLoginId(),
                        loginDto.getPassword()
                );
        // 2. 생성된 UsernamePasswordAuthenticationToken을 authenticationManager에게 전달.
        // 3. authenticationManager은 UserDetailsService의 loadUserByUsername을 호출 (DB에 있는 유저정보 UserDetails 객체를 불러옴)
        // 4. 조회된 유저정보(UserDetails와 UsernamePasswordAuthenticationToken을 비교해 인증처리.
        Authentication authentication = authenticationManager.authenticate(authToken);
        // 인증 완.

        // jwt 토큰 생성
        // 권한(Role) 추출 : 현재 인증된 사용자의 권한 리스트(GrantedAuthority)를 가져옴.
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(auth -> auth.getAuthority())   // "ROLE_ADMIN" or "ROLE_USER"
                .orElse(null);

        // 5. 최종 반환된 Authentication(인증된 유저 정보)를 기반으로 JWT TOKEN 발급
        String jwtToken = "Bearer " + jwtService.generateToken(authentication.getName(), role);

        Member member = memberRepository.findByLoginId(loginDto.getLoginId())
                .orElseThrow(() -> new RuntimeException("회원 없음"));

        File file = fileService.findFirstByTargetIdAndTargetType(member.getId(), TargetType.MEMBER);


        return LoginResponseDto.builder()
                .token(jwtToken)
                .memberId(member.getId())
                .name(member.getName())
                .jobPosition(
                        Optional.ofNullable(member.getJobPosition())
                                .map(job -> job.getName())
                                .orElse("") // null이면 빈 문자열로 대체
                )
                .profileUrl(
                        Optional.ofNullable(file)
                                .map(File::getPath)
                                .orElse("")// null이면 빈 문자열로 대체
                )
                .role(String.valueOf(member.getRole()))
                .build();
    }
}
