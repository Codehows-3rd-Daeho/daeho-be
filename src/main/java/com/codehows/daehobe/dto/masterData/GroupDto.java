package com.codehows.daehobe.dto.masterData;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GroupDto {
    private String groupName;
    private List<Long> memberIds = new ArrayList<>();

}
