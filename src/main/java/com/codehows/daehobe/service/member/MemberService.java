package com.codehows.daehobe.service.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.dto.masterData.PartMemberDto;
import com.codehows.daehobe.dto.masterData.PartMemberListDto;
import com.codehows.daehobe.dto.member.MemberDto;
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

import java.util.List;
import java.util.stream.Collectors;

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
        Department dpt  =  departmentRepository.findById(memberDto.getDepartmentId()).orElseThrow(EntityNotFoundException::new);

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

    //주관자 조회
    //조회 후 Entity를 Dto로 변환하여 반환
    public PartMemberDto findHostById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found with id: " + id));

        return PartMemberDto.fromEntity(member);
    }

    /*
    DB에서 Member 리스트를 가져옴
    → 하나씩 DTO로 변환하고
    → 변환된 DTO들을 한 번에 리스트로 담아서 반환하는 코드
     */
    public List<PartMemberListDto> findAll() {
        return memberRepository.findAll()
                .stream()
                .map(PartMemberListDto::fromEntity)
                .collect(Collectors.toList());

    }


}
