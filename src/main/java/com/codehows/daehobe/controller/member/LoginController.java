package com.codehows.daehobe.controller.member;

import com.codehows.daehobe.dto.member.LoginDto;
import com.codehows.daehobe.service.member.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
// 사용자가 로그인할때 JWT 토큰을 발급해주는 컨트롤러.
public class LoginController {
    private final LoginService loginService;

    /**
     * 로그인 처리 후 JWT 발급
     * ID/PW를 주면 -> 인증을 거쳐 -> JWT토큰을 만들어 브라우저에게 제공.
     *
     * @param loginDto 로그인 요청 DTO
     * @return 발급된 JWT 토큰
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDto loginDto, BindingResult bindingResult) {
        // DTO 검증 실패 시
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldError().getDefaultMessage(); // 첫 번째 에러 메시지
            return ResponseEntity.badRequest().body(errorMessage);
        }
        String jwtToken = loginService.login(loginDto);

        // 응답 헤더(Authorication)에 Bearer <JWT TOKEN VALUE> 형태로 응답
        // 이후 클라이언트는 이 토큰을 가지고 다른 API 요청시 Authorization 헤더에 넣어 인증을 받게됨.
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .build();
    }

}