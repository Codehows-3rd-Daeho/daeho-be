package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.GroupDto;
import com.codehows.daehobe.masterData.dto.GroupListDto;
import com.codehows.daehobe.masterData.entity.CustomGroup;
import com.codehows.daehobe.masterData.entity.CustomGroupMember;
import com.codehows.daehobe.masterData.repository.CustomGroupMemberRepository;
import com.codehows.daehobe.masterData.repository.CustomGroupRepository;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private CustomGroupRepository customGroupRepository;
    @Mock
    private CustomGroupMemberRepository customGroupMemberRepository;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private GroupService groupService;

    @Test
    @DisplayName("성공: 그룹 생성")
    void createGroup_Success() {
        // given
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("테스트그룹");
        groupDto.setMemberIds(Arrays.asList(1L, 2L));
        CustomGroup group = CustomGroup.builder().id(1L).name("테스트그룹").build();
        Member member1 = Member.builder().id(1L).name("회원1").email("a@a").loginId("a").phone("a").password("a").isEmployed(true).build();
        Member member2 = Member.builder().id(2L).name("회원2").email("b@b").loginId("b").phone("b").password("b").isEmployed(true).build();

        when(customGroupRepository.save(any(CustomGroup.class))).thenReturn(group);
        when(memberService.getMemberById(1L)).thenReturn(member1);
        when(memberService.getMemberById(2L)).thenReturn(member2);

        // when
        groupService.createGroup(groupDto);

        // then
        verify(customGroupRepository).save(any(CustomGroup.class));
        verify(customGroupMemberRepository).saveAll(anyList());
        verify(memberService, times(2)).getMemberById(anyLong());
    }

    @Test
    @DisplayName("성공: 그룹 목록 조회")
    void getGroupList_Success() {
        // given
        CustomGroup group = CustomGroup.builder().id(1L).name("테스트그룹").build();
        Member member = Member.builder().id(1L).email("test@test.com").name("테스트").loginId("test").phone("123").password("pass").isEmployed(true).build();
        CustomGroupMember cgm = CustomGroupMember.builder().customGroup(group).member(member).build();

        when(customGroupRepository.findAll()).thenReturn(Collections.singletonList(group));
        when(customGroupMemberRepository.findAllByCustomGroupId(1L)).thenReturn(Collections.singletonList(cgm));

        // when
        List<GroupListDto> result = groupService.getGroupList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("테스트그룹");
        assertThat(result.get(0).getMembers()).hasSize(1);
        assertThat(result.get(0).getMembers().get(0).getName()).isEqualTo("테스트");
    }
    
    @Test
    @DisplayName("성공: 그룹 목록이 비어있을 때 조회")
    void getGroupList_Empty() {
        // given
        when(customGroupRepository.findAll()).thenReturn(new ArrayList<>());
        
        // when
        List<GroupListDto> result = groupService.getGroupList();

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("성공: 그룹 업데이트")
    void updateGroup_Success() {
        // given
        Long groupId = 1L;
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("수정된그룹");
        groupDto.setMemberIds(Arrays.asList(3L));
        CustomGroup group = CustomGroup.builder().id(groupId).name("원본그룹").build();
        Member newMember = Member.builder().id(3L).name("새회원").email("c@c").loginId("c").phone("c").password("c").isEmployed(true).build();

        when(customGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(memberService.getMemberById(3L)).thenReturn(newMember);

        // when
        groupService.updateGroup(groupId, groupDto);

        // then
        assertThat(group.getName()).isEqualTo("수정된그룹");
        verify(customGroupMemberRepository).deleteByCustomGroup(group);
        verify(customGroupMemberRepository).save(any(CustomGroupMember.class));
    }

    @Test
    @DisplayName("실패: 업데이트할 그룹 없음")
    void updateGroup_NotFound() {
        // given
        Long groupId = 99L;
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("수정된그룹");
        groupDto.setMemberIds(Arrays.asList(1L));
        when(customGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> groupService.updateGroup(groupId, groupDto));
    }

    @Test
    @DisplayName("성공: 그룹 삭제")
    void deleteGroup_Success() {
        // given
        Long groupId = 1L;
        CustomGroup group = CustomGroup.builder().id(groupId).name("삭제할그룹").build();
        when(customGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // when
        groupService.deleteGroup(groupId);

        // then
        verify(customGroupMemberRepository).deleteByCustomGroup(group);
        verify(customGroupRepository).delete(group);
    }

    @Test
    @DisplayName("실패: 삭제할 그룹 없음")
    void deleteGroup_NotFound() {
        // given
        Long groupId = 99L;
        when(customGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> groupService.deleteGroup(groupId));
    }
}
