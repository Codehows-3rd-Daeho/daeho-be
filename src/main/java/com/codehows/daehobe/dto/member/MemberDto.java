package com.codehows.daehobe.dto.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.member.Member;
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
    @Pattern(regexp = "^[^\\sㄱ-ㅎ가-힣]{8,20}$", message = "비밀번호는 8~20자로 입력하세요.")
    private String password;

    // 이름
    private String name;

    // 부서
    private Long departmentId;

    // 직급
    private Long jobPositionId;

    // 전화번호
    private String phone;

    // 이메일
    private String email;

    // 재직 여부
    private Boolean isEmployed;

    // 관리자 여부
    private String role;

    // 프로필 이미지 id
    private Long profileFileId;

    // 프로필 이미지 url
    private String profileUrl;

    public static MemberDto fromEntity(Member member, File profileFile) {
        return MemberDto.builder()
                .id(member.getId())
                .loginId(member.getLoginId())
                .name(member.getName())
                .departmentId(member.getDepartment() != null ? member.getDepartment().getId() : null)
                .jobPositionId(member.getJobPosition() != null ? member.getJobPosition().getId() : null)
                .phone(member.getPhone())
                .email(member.getEmail())
                .isEmployed(member.getIsEmployed())
                .role(String.valueOf(member.getRole()))
                .profileUrl(profileFile != null ? profileFile.getPath() : null)
                .profileFileId(profileFile != null ? profileFile.getFileId() : null)
                .build();
    }
}
