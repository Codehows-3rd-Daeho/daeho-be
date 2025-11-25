package com.codehows.daehobe.service.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.dto.MemberDto;
import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.entity.JobPosition;
import com.codehows.daehobe.entity.Member;
import com.codehows.daehobe.repository.DepartmentRepository;
import com.codehows.daehobe.repository.JobPositionRepository;
import com.codehows.daehobe.repository.MemberRepository;
import com.codehows.daehobe.service.masterData.JobPositionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final JobPositionRepository jobPositionRepository;
    private final DepartmentRepository departmentRepository;

    public Member createMember(@Valid MemberDto memberDto) {
        JobPosition pos = jobPositionRepository.findById(memberDto.getJobPositionId()).orElseThrow(EntityNotFoundException::new);
        Department dpt  =  departmentRepository.findById(memberDto.getDepartmentId()).orElseThrow(EntityNotFoundException::new);

        // DTO → Entity 변환
        Member member = Member.builder()
                .loginId(memberDto.getLoginId())
                .password(memberDto.getPassword())
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
}
