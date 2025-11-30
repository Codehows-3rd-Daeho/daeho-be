package com.codehows.daehobe.service.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.masterData.JobPositionRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final JobPositionRepository jobPositionRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public Member createMember(@Valid MemberDto memberDto) {
        JobPosition pos = jobPositionRepository.findById(memberDto.getJobPositionId()).orElseThrow(EntityNotFoundException::new);
        Department dpt = departmentRepository.findById(memberDto.getDepartmentId()).orElseThrow(EntityNotFoundException::new);

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

        // DB 저장
        return memberRepository.save(member);
    }

    public List<MemberListDto> findAll() {
        List<Member> memberList = memberRepository.findAll();
        List<MemberListDto> dtoList = new ArrayList<>();

        for (Member member : memberList) {
            MemberListDto dto = MemberListDto.builder()
                    .id(member.getId())
                    .name(member.getName())
                    .departmentName(member.getDepartment().getName())
                    .jobPositionName(member.getJobPosition().getName())
                    .phone(member.getPhone())
                    .email(member.getEmail())
                    .isEmployed(member.getIsEmployed())
                    .build();
            dtoList.add(dto);
        }
        return dtoList;
    }
}
