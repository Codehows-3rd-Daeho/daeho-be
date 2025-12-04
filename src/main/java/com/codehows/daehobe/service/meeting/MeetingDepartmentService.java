package com.codehows.daehobe.service.meeting;

import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.entity.meeting.Meeting;
import com.codehows.daehobe.entity.meeting.MeetingDepartment;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.meeting.MeetingDepartmentRepository;
import com.codehows.daehobe.repository.meeting.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

}
