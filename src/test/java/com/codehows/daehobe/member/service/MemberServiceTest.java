package com.codehows.daehobe.member.service;

import com.codehows.daehobe.common.constant.Role;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.service.DepartmentService;
import com.codehows.daehobe.masterData.service.JobPositionService;
import com.codehows.daehobe.member.dto.MemberDto;
import com.codehows.daehobe.member.dto.MemberListDto;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JobPositionService jobPositionService;
    @Mock
    private DepartmentService departmentService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FileService fileService;
    
    @InjectMocks
    private MemberService memberService;

    private Member testMember;
    private MemberDto testMemberDto;
    private Department testDepartment;
    private JobPosition testJobPosition;

    @BeforeEach
    void setUp() {
        testDepartment = Department.builder().id(1L).name("개발부").build();
        testJobPosition = JobPosition.builder().id(1L).name("개발자").build();

        testMember = Member.builder()
                .id(1L)
                .loginId("testUser")
                .password("encodedPassword")
                .name("테스터")
                .department(testDepartment)
                .jobPosition(testJobPosition)
                .phone("010-1234-5678")
                .email("test@test.com")
                .isEmployed(true)
                .role(Role.USER)
                .build();
        
        testMemberDto = MemberDto.fromEntity(testMember, null);
    }

    @Test
    @DisplayName("성공: 회원 생성")
    void createMember_Success() {
        // given
        MemberDto createDto = MemberDto.builder()
                .loginId("newUser")
                .password("password")
                .name("새로운 사용자")
                .departmentId(1L)
                .jobPositionId(1L)
                .phone("010-0000-0000")
                .email("new@test.com")
                .isEmployed(true)
                .role("USER")
                .build();

        when(jobPositionService.getJobPositionById(1L)).thenReturn(testJobPosition);
        when(departmentService.getDepartmentById(1L)).thenReturn(testDepartment);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        // repository.save가 어떤 Member 객체를 반환하든 중요하지 않음.
        // 우리는 save에 어떤 객체가 전달되는지를 검증할 것이기 때문.
        when(memberRepository.save(any(Member.class))).thenReturn(testMember);

        // when
        memberService.createMember(createDto, Collections.emptyList());

        // then
        // ArgumentCaptor를 사용하여 memberRepository.save 메소드에 전달된 Member 객체를 캡처
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        
        // 캡처된 Member 객체의 속성이 DTO의 속성과 일치하는지 검증
        Member capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.getLoginId()).isEqualTo(createDto.getLoginId());
        assertThat(capturedMember.getName()).isEqualTo(createDto.getName());
        assertThat(capturedMember.getPassword()).isEqualTo("encodedPassword");
        assertThat(capturedMember.getDepartment()).isEqualTo(testDepartment);
        assertThat(capturedMember.getJobPosition()).isEqualTo(testJobPosition);
        
        // 파일 업로드 호출 검증
        verify(fileService).uploadFiles(testMember.getId(), Collections.emptyList(), TargetType.MEMBER);
    }

    @Test
    @DisplayName("성공: 회원 목록 조회")
    void findAll_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Member> memberList = Collections.singletonList(testMember);
        Page<Member> memberPage = new PageImpl<>(memberList, pageable, memberList.size());

        when(memberRepository.searchMembers(anyString(), any(Pageable.class))).thenReturn(memberPage);

        // when
        Page<MemberListDto> result = memberService.findAll(pageable, "test");

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo(testMember.getName());
    }

    @Test
    @DisplayName("성공: 회원 상세 조회")
    void getMemberDtl_Success() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
        when(fileService.findFirstByTargetIdAndTargetType(anyLong(), any())).thenReturn(null);

        // when
        MemberDto result = memberService.getMemberDtl(1L);

        // then
        assertThat(result.getName()).isEqualTo(testMember.getName());
    }

    @Test
    @DisplayName("실패: 회원 상세 조회 (회원 없음)")
    void getMemberDtl_NotFound() {
        // given
        when(memberRepository.findById(anyLong())).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> memberService.getMemberDtl(99L));
    }

    @Test
    @DisplayName("성공: 임시 비밀번호 생성")
    void generatePwd_Success() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
        when(passwordEncoder.encode(anyString())).thenReturn("newEncodedPassword");

        // when
        String newPassword = memberService.generatePwd(1L);

        // then
        assertThat(newPassword).isNotNull();
        assertThat(newPassword.length()).isEqualTo(8);
        verify(memberRepository).findById(1L);
        verify(passwordEncoder).encode(anyString());
        assertThat(testMember.getPassword()).isEqualTo("newEncodedPassword");
    }

    @Test
    @DisplayName("성공: 회원 정보 수정")
    void updateMember_Success() {
        // given
        MemberDto updateDto = MemberDto.builder()
                .name("수정된테스터")
                .departmentId(2L)
                .jobPositionId(2L)
                .build();
        Department newDept = Department.builder().id(2L).name("기획부").build();
        JobPosition newPos = JobPosition.builder().id(2L).name("기획자").build();

        when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
        when(departmentService.getDepartmentById(2L)).thenReturn(newDept);
        when(jobPositionService.getJobPositionById(2L)).thenReturn(newPos);

        // when
        memberService.updateMember(1L, updateDto, Collections.emptyList(), Collections.emptyList());

        // then
        assertThat(testMember.getName()).isEqualTo("수정된테스터");
        assertThat(testMember.getDepartment().getName()).isEqualTo("기획부");
        verify(fileService).updateFiles(anyLong(), anyList(), anyList(), any());
    }
    
    @Test
    @DisplayName("성공: ID로 회원 조회")
    void getMemberById_Success() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));

        // when
        Member foundMember = memberService.getMemberById(1L);

        // then
        assertThat(foundMember).isEqualTo(testMember);
    }

    @Test
    @DisplayName("실패: ID로 회원 조회 (회원 없음)")
    void getMemberById_NotFound() {
        // given
        when(memberRepository.findById(anyLong())).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> memberService.getMemberById(99L));
    }
}
