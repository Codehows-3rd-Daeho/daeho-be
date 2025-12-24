package com.codehows.daehobe.controller.masterData;

import com.codehows.daehobe.dto.masterData.GroupDto;
import com.codehows.daehobe.dto.masterData.GroupListDto;
import com.codehows.daehobe.service.masterData.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping("/masterData/group")
    public ResponseEntity<?> getGroupList() {
        try {
            List<GroupListDto> dto = groupService.getGroupList();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("그룹 조회 중 오류 발생");
        }
    }

    @PostMapping("/admin/group")
    public ResponseEntity<?> createGroup(@RequestBody GroupDto groupDto){
        try{
            groupService.createGroup(groupDto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("그룹 등록 중 오류 발생");
        }
    }

    @PutMapping("/admin/group/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable Long id, @RequestBody GroupDto groupDto){
        try{
            groupService.updateGroup(id, groupDto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("그룹 수정 중 오류 발생");
        }
    }


    @DeleteMapping("/admin/group/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id) {
        try {
            groupService.deleteGroup(id);
            return ResponseEntity.noContent().build();
        }  catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("그룹 삭제 중 오류 발생");
        }
    }
}
