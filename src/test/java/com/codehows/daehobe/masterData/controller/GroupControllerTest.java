package com.codehows.daehobe.masterData.controller;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.config.jwtAuth.JwtService;
import com.codehows.daehobe.masterData.dto.GroupDto;
import com.codehows.daehobe.masterData.dto.GroupListDto;
import com.codehows.daehobe.masterData.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(PerformanceLoggingExtension.class)
@WebMvcTest(GroupController.class)
@Import(JwtService.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    private final String ADMIN_API_PATH_PREFIX = "/admin/group";
    private final String PUBLIC_API_PATH_PREFIX = "/masterData/group";

    @Test
    @DisplayName("성공: 모든 그룹 조회")
    @WithMockUser
    void getGroupList_Success() throws Exception {
        // given
        given(groupService.getGroupList()).willReturn(Arrays.asList(
                new GroupListDto(1L, "그룹1", Collections.emptyList()),
                new GroupListDto(2L, "그룹2", Collections.emptyList())
        ));

        // when
        ResultActions result = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX).with(csrf()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("그룹1"))
                .andExpect(jsonPath("$[1].groupName").value("그룹2"));
    }

    @Test
    @DisplayName("실패: 그룹 조회 중 내부 서버 오류")
    @WithMockUser
    void getGroupList_InternalServerError() throws Exception {
        // given
        given(groupService.getGroupList()).willThrow(new RuntimeException("서비스 오류"));

        // when
        ResultActions result = mockMvc.perform(get(PUBLIC_API_PATH_PREFIX).with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("그룹 조회 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 그룹 생성")
    @WithMockUser
    void createGroup_Success() throws Exception {
        // given
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("새그룹");
        groupDto.setMemberIds(Arrays.asList(1L));

        doNothing().when(groupService).createGroup(any(GroupDto.class));

        // when
        ResultActions result = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(groupDto))
                .with(csrf()));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("실패: 그룹 생성 중 내부 서버 오류")
    @WithMockUser
    void createGroup_InternalServerError() throws Exception {
        // given
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("새그룹");
        groupDto.setMemberIds(Arrays.asList(1L));

        doThrow(new RuntimeException("서비스 오류")).when(groupService).createGroup(any(GroupDto.class));

        // when
        ResultActions result = mockMvc.perform(post(ADMIN_API_PATH_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(groupDto))
                .with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("그룹 등록 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 그룹 수정")
    @WithMockUser
    void updateGroup_Success() throws Exception {
        // given
        Long groupId = 1L;
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("수정된그룹");
        groupDto.setMemberIds(Arrays.asList(2L));

        doNothing().when(groupService).updateGroup(anyLong(), any(GroupDto.class));

        // when
        ResultActions result = mockMvc.perform(put(ADMIN_API_PATH_PREFIX + "/{id}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(groupDto))
                .with(csrf()));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("실패: 그룹 수정 중 내부 서버 오류")
    @WithMockUser
    void updateGroup_InternalServerError() throws Exception {
        // given
        Long groupId = 1L;
        GroupDto groupDto = new GroupDto();
        groupDto.setGroupName("수정된그룹");
        groupDto.setMemberIds(Arrays.asList(2L));

        doThrow(new RuntimeException("서비스 오류")).when(groupService).updateGroup(anyLong(), any(GroupDto.class));

        // when
        ResultActions result = mockMvc.perform(put(ADMIN_API_PATH_PREFIX + "/{id}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(groupDto))
                .with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("그룹 수정 중 오류 발생"));
    }

    @Test
    @DisplayName("성공: 그룹 삭제")
    @WithMockUser
    void deleteGroup_Success() throws Exception {
        // given
        Long groupId = 1L;
        doNothing().when(groupService).deleteGroup(groupId);

        // when
        ResultActions result = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", groupId)
                .with(csrf()));

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("실패: 그룹 삭제 중 내부 서버 오류")
    @WithMockUser
    void deleteGroup_InternalServerError() throws Exception {
        // given
        Long groupId = 1L;
        doThrow(new RuntimeException("서비스 오류")).when(groupService).deleteGroup(groupId);

        // when
        ResultActions result = mockMvc.perform(delete(ADMIN_API_PATH_PREFIX + "/{id}", groupId)
                .with(csrf()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("그룹 삭제 중 오류 발생"));
    }
}
