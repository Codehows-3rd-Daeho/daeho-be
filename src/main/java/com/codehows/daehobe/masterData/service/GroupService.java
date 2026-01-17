package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.GroupDto;
import com.codehows.daehobe.masterData.dto.GroupListDto;
import com.codehows.daehobe.member.dto.PartMemberListDto;
import com.codehows.daehobe.masterData.entity.CustomGroup;
import com.codehows.daehobe.masterData.entity.CustomGroupMember;
import com.codehows.daehobe.masterData.entity.CustomGroupMemberId;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.masterData.repository.CustomGroupMemberRepository;
import com.codehows.daehobe.masterData.repository.CustomGroupRepository;
import com.codehows.daehobe.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class GroupService {
    private final CustomGroupRepository customGroupRepository;
    private final CustomGroupMemberRepository customGroupMemberRepository;
    private final MemberService memberService;

    public void createGroup(GroupDto groupDto) {
        // 그룹 생성
        CustomGroup customGroup = CustomGroup.builder()
                .name(groupDto.getGroupName())
                .build();
        customGroupRepository.save(customGroup);

        // 그룹-회원 매핑 생성
        List<CustomGroupMember> members = new ArrayList<>();

        for (Long memberId : groupDto.getMemberIds()) {
            Member member = memberService.getMemberById(memberId);
            CustomGroupMember customGroupMember = CustomGroupMember.builder()
                    .id(new CustomGroupMemberId(customGroup.getId(), memberId))
                    .customGroup(customGroup)
                    .member(member)
                    .build();
            members.add(customGroupMember);
        }
        customGroupMemberRepository.saveAll(members);
    }

    public List<GroupListDto> getGroupList() {
        List<CustomGroup> groups = customGroupRepository.findAll();
        List<GroupListDto> result = new ArrayList<>();

        for (CustomGroup group : groups) {
            List<CustomGroupMember> groupMembers = customGroupMemberRepository.findAllByCustomGroupId(group.getId());
            List<PartMemberListDto> members = new ArrayList<>();

            for (CustomGroupMember gm : groupMembers) {
                members.add(PartMemberListDto.fromEntity(gm.getMember()));
            }

            result.add(new GroupListDto(group.getId(), group.getName(), members));
        }

        return result;
    }

    public void updateGroup(Long groupId, GroupDto groupDto) {
        // 1. 그룹 조회
        CustomGroup group = customGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("그룹이 존재하지 않습니다."));
        if (groupDto.getGroupName() != null) {
            group.updateName(groupDto.getGroupName());
        }

        customGroupMemberRepository.deleteByCustomGroup(group);
        for (Long memberId : groupDto.getMemberIds()) {
            Member member = memberService.getMemberById(memberId);
            CustomGroupMember cgm = CustomGroupMember.builder()
                    .id(new CustomGroupMemberId(group.getId(), member.getId()))
                    .customGroup(group)
                    .member(member)
                    .build();
            customGroupMemberRepository.save(cgm);
        }
    }


    public void deleteGroup(Long id) {
        CustomGroup group = customGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("그룹이 존재하지 않습니다."));
        customGroupMemberRepository.deleteByCustomGroup(group);
        customGroupRepository.delete(group);
    }

}
