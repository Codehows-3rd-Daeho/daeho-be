package com.codehows.daehobe.meeting.service;

import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.meeting.entity.Meeting;
import com.codehows.daehobe.meeting.entity.MeetingDepartment;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import com.codehows.daehobe.meeting.repository.MeetingDepartmentRepository;
import com.codehows.daehobe.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class MeetingDepartmentService {

    private final MeetingRepository meetingRepository;
    private final DepartmentRepository departmentRepository;
    private final MeetingDepartmentRepository meetingDepartmentRepository;

    public List<MeetingDepartment> saveDepartment(Long meetingId, List<Long> departmentIds) {

        //1. 회의 조회
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("meeting not found"));

        //2. 부서 조회
        List<Department> departments = departmentRepository.findByIdIn(departmentIds);

        //3. 회의 부서 엔티티 저장
        List<MeetingDepartment> meetingDepartmentList = departments.stream()
                .map(department -> new MeetingDepartment(meeting, department))
                        .collect(Collectors.toList());

        meetingDepartmentRepository.saveAll(meetingDepartmentList);
        return meetingDepartmentList;
    }

    // 미팅 > 부서 찾기
    public List<String> getDepartmentName(Meeting meeting) {
        return meetingDepartmentRepository.findByMeeting(meeting).stream()
                .map(d -> d.getDepartment().getName())
                .toList();
    }

    // 이슈로 부서 엔티티 찾기
    public List<MeetingDepartment> getDepartMent(Meeting meeting){
        return meetingDepartmentRepository.findByMeeting(meeting);
    }

    // 이슈 부서 삭제
    public void deleteMeetingDepartment(Meeting meeting) {
        meetingDepartmentRepository.deleteByMeeting(meeting);
    }

}
