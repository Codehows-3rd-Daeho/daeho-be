package com.codehows.daehobe.service.member;

import com.codehows.daehobe.constant.Role;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.dto.comment.MentionMemberDto;
import com.codehows.daehobe.dto.meeting.MeetingListDto;
import com.codehows.daehobe.dto.member.MemberProfileDto;
import com.codehows.daehobe.dto.member.PartMemberListDto;
import com.codehows.daehobe.dto.member.MemberDto;
import com.codehows.daehobe.dto.member.MemberListDto;
import com.codehows.daehobe.entity.comment.Comment;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.masterData.JobPosition;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingMember;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.file.FileRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.service.masterData.DepartmentService;
import com.codehows.daehobe.service.masterData.JobPositionService;
import com.codehows.daehobe.service.meeting.MeetingDepartmentService;
import com.codehows.daehobe.service.meeting.MeetingMemberService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final MeetingMemberService meetingMemberService;
    private final MeetingDepartmentService meetingDepartmentService;

    public Member createMember(@Valid MemberDto memberDto, List<MultipartFile> profileImage) {
        JobPosition pos = jobPositionService.getJobPositionById(memberDto.getJobPositionId());
        Department dpt = departmentService.getDepartmentById(memberDto.getDepartmentId());
        Role role = "ADMIN".equals(memberDto.getRole()) ? Role.ADMIN : Role.USER;

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
                .role(role)
                .build();

        // 회원저장
        memberRepository.save(member);

        // 파일저장
        if (profileImage != null) {
            fileService.uploadFiles(member.getId(), profileImage, TargetType.MEMBER);
        }

        return member;
    }

    public Page<MemberListDto> findAll(Pageable pageable, String keyword) {
        String searchKw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();

        return memberRepository.searchMembers(searchKw, pageable)
                .map(MemberListDto::fromEntity);
    }

    public MemberDto getMemberDtl(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));
        File profileFile = fileRepository.findFirstByTargetIdAndTargetType(id, TargetType.MEMBER).orElse(null);

        return MemberDto.fromEntity(member, profileFile);
    }

    //마이페이지 회원 조회
    public MemberProfileDto getMemberProfile(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));
        File profileFile = fileRepository.findFirstByTargetIdAndTargetType(id, TargetType.MEMBER).orElse(null);
        ;
        return MemberProfileDto.fromEntity(member, profileFile);
    }

    //관리자 비밀번호 생성
    public String generatePwd(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));

        // 임시 비밀번호 생성
        String newPwd = generateTempPassword();
        String encodedPassword = passwordEncoder.encode(newPwd);
        member.updatePassword(encodedPassword);
        return newPwd;
    }

    //비밀번호 재설정
    public void changPwd(String newPwd) {
        System.out.println("=========================================================================");
        System.out.println("newPwd: " + newPwd);
        System.out.println("=========================================================================");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();//인증정보

        String memberIdStr = (String) authentication.getPrincipal();;//principal 추출
        Long memberId = Long.parseLong(memberIdStr);

        Member member = memberRepository.findById(memberId)//멤버 정보
                .orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));

        //멤버의 pw만 변경
        String encodingPwd = passwordEncoder.encode(newPwd);
        member.updatePassword(encodingPwd);
        System.out.println("encodingPwd: " + encodingPwd);
        System.out.println("member.getPassword: " + member.getPassword());


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
        return memberRepository.findByIsEmployedTrue()
                .stream()
                .map(PartMemberListDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 아이디로 멤버 찾기
    public Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다."));
    }

    // 멘션 멤버조회
    public List<MentionMemberDto> searchForMention(String keyword) {
        return memberRepository.searchForMention(keyword);
    }

    // 멘션 회원
    public List<Member> findMembersByIds(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }

        return memberRepository.findByIdIn(memberIds);
    }

    //================================================나의 업무=================================================================
    public Page<MeetingListDto> getMeetingsForMember(Long memberId, Pageable pageable) {

        Page<MeetingMember> meetingMembers = meetingMemberService.findByMemberId(memberId, pageable);

        return meetingMembers.map(mm ->
                toMeetingListDto(mm.getMeeting())
        );
    }

    //Entity -> Dto, 주관자 정보, 부서 정보
    private MeetingListDto toMeetingListDto(Meeting meeting) {

        MeetingMember host = meetingMemberService.getHost(meeting);
        String hostName = (host != null) ? host.getMember().getName() : null;
        String hostJPName = (host != null && host.getMember().getJobPosition() != null) ? host.getMember().getJobPosition().getName() : null;
        List<String> departmentName = meetingDepartmentService.getDepartmentName(meeting);
        return MeetingListDto.fromEntity(meeting, departmentName, hostName, hostJPName);

    }


}
