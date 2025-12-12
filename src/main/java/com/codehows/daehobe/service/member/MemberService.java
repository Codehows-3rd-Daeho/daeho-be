package com.codehows.daehobe.service.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.member.PartMemberListDto;
import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.DepartmentService;
import com.codehows.daehobe.service.masterData.JobPositionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final JobPositionService jobPositionService;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final FileRepository fileRepository;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    public Member createMember(@Valid MemberDto memberDto, List<MultipartFile> profileImage) {
        JobPosition pos = jobPositionService.getJobPositionById(memberDto.getJobPositionId());
        Department dpt = departmentService.getDepartmentById(memberDto.getDepartmentId());

        // DTO → Entity 변환
        Member member = Member.builder()
                .loginId(memberDto.getLoginId())
                .password(passwordEncoder.encode(memberDto.getPassword()))
                .name(memberDto.getName())
                .department(dpt)
                .jobPosition(pos)
                .phone(memberDto.getPhone())
                .email(memberDto.getEmail())
                .isEmployed(memberDto.getIsEmployed())
                .role(Role.USER)
                .build();

        // 회원저장
        memberRepository.save(member);

        // 파일저장
        if (profileImage != null) {
            fileService.uploadFiles(member.getId(), profileImage, TargetType.MEMBER);
        }

        return member;
    }

    public Page<MemberListDto> findAll(Pageable pageable) {
        return memberRepository.findAll(pageable)
                .map(MemberListDto::fromEntity);
    }

    public MemberDto getMemberDtl(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));
        File profileFile = fileRepository.findFirstByTargetIdAndTargetType(id, TargetType.MEMBER).orElse(null);

        String profileUrl = (profileFile != null) ? profileFile.getPath() : null;
        return MemberDto.fromEntity(member, profileFile);
    }

    public String generatePwd(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));

        // 임시 비밀번호 생성
        String newPwd = generateTempPassword();
        String encodedPassword = passwordEncoder.encode(newPwd);
        member.updatePassword(encodedPassword);
        return newPwd;
    }

    public Member updateMember(Long id,
            MemberDto memberDto,
            List<MultipartFile> newFiles,
            List<Long> removeFileIds) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));

        JobPosition pos = jobPositionService.getJobPositionById(memberDto.getJobPositionId());
        Department dpt = departmentService.getDepartmentById(memberDto.getDepartmentId());

        member.update(memberDto, dpt, pos, passwordEncoder);

        // 파일 업데이트
        fileService.updateFiles(member.getId(), newFiles, removeFileIds, TargetType.MEMBER);

        return member;
    }

    // 8자 영숫자 임시 비밀번호 생성
    private String generateTempPassword() {
        return random.ints(PASSWORD_LENGTH, 0, CHARACTERS.length())
                .mapToObj(CHARACTERS::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }

    // role이 "USER"이고 isEmployed가 true 인 Member 리스트
    public List<PartMemberListDto> findByRoleAndIsEmployedTrue() {
        return memberRepository.findByRoleAndIsEmployedTrue(Role.USER)
                .stream()
                .map(PartMemberListDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 아이디로 멤버 찾기
    public Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));
    }

    public String getMemberNameById(Long id) {
        return getMemberById(id).getName();
    }
}
