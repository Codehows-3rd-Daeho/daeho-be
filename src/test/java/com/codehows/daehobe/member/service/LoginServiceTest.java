package com.codehows.daehobe.member.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.common.constant.Role;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.member.dto.LoginDto;
import com.codehows.daehobe.member.dto.LoginResponseDto;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class LoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FileService fileService;

    @InjectMocks
    private LoginService loginService;

    private LoginDto createLoginDto(String loginId, String password) {
        return new LoginDto(loginId, password);
    }

    @Test
    @DisplayName("성공: 로그인")
    void login_Success() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("testUser", "password");
        Member member = Member.builder()
                .id(1L)
                .loginId("testUser")
                .name("테스터")
                .role(Role.USER)
                .build();
        
        Authentication authentication = mock(Authentication.class);
        Collection<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        
        when(authentication.getName()).thenReturn("testUser");
        when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateToken(any(), any())).thenReturn("dummy.jwt.token");
        when(memberRepository.findByLoginId("testUser")).thenReturn(Optional.of(member));
        when(fileService.findFirstByTargetIdAndTargetType(any(), any())).thenReturn(null);

        // when
        LoginResponseDto response = loginService.login(loginDto);

        // then
        assertThat(response.getToken()).isEqualTo("Bearer dummy.jwt.token");
        assertThat(response.getName()).isEqualTo("테스터");
        assertThat(response.getRole()).isEqualTo("USER");
    }
    
    @Test
    @DisplayName("실패: 로그인 (잘못된 자격 증명)")
    void login_Failure_BadCredentials() throws Exception {
        // given
        LoginDto loginDto = createLoginDto("testUser", "wrongPassword");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("자격 증명 실패"));
        
        // when & then
        assertThrows(BadCredentialsException.class, () -> loginService.login(loginDto));
    }
}
