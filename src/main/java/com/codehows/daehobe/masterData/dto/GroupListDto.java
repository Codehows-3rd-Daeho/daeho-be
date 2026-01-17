package com.codehows.daehobe.masterData.dto;

import com.codehows.daehobe.member.dto.PartMemberListDto;
import com.codehows.daehobe.masterData.entity.CustomGroup;
import com.codehows.daehobe.masterData.entity.CustomGroupMember;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GroupListDto {
    private Long id;
    private String groupName;
    private List<PartMemberListDto> members;

    public static GroupListDto fromEntity(CustomGroup group, List<CustomGroupMember> groupMembers) {
        List<PartMemberListDto> memberDtos = groupMembers.stream()
                .map(gm -> PartMemberListDto.fromEntity(gm.getMember()))
                .toList();
        return new GroupListDto(group.getId(), group.getName(), memberDtos);
    }
}
