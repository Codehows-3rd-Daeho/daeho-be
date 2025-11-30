package com.codehows.daehobe.dto.member;

import com.codehows.daehobe.repository.masterData.JobPositionRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberDto {

    private Long id;

    // 로그인ID : 영문 또는 숫자 4~20자
    @NotBlank(message = "아이디는 필수 입력값입니다.")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "아이디는 영문 또는 숫자 4~20자로 입력하세요.")
    private String loginId;

    // 비밀번호: 공백/한글 제외 8~20자
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(regexp = "^[^\\sㄱ-ㅎ가-힣]{8,20}$", message = "비밀번호는 8~20자로 입력하세요.")
    private String password;

    // 이름
    @NotBlank(message = "이름은 필수 입력값입니다.")
    private String name;

    // 부서
    private Long departmentId;

    // 직급
    private Long jobPositionId;

    // 전화번호: 숫자, 하이픈 허용 + 하이픈 필수
    @NotBlank(message = "전화번호는 필수 입력값입니다.")
    @Pattern(regexp = "^[0-9]+(-[0-9]+)+$", message = "전화번호는 숫자와 하이픈(-)을 포함해야 합니다.")
    private String phone;

    // 이메일
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    // 재직 여부
    @NotNull(message = "재직 여부를 선택해주세요.")
    private Boolean isEmployed;

    // 프로필 이미지 url
    private String profileUrl;

    // 프로필 이미지 파일명
    private String profileFilename;

}
