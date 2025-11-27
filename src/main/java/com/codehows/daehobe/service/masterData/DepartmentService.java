package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.Department;
import com.codehows.daehobe.repository.masterData.DepartmentRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final MemberRepository memberRepository;

    public List<MasterDataDto> findAll() {
        List<Department> dpts = departmentRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (Department dpt : dpts) {
            dtoList.add(new MasterDataDto(dpt.getId(), dpt.getName()));
        }
        return dtoList;
    }

    public Department createDpt(MasterDataDto masterDataDto) {
        Department department = Department.builder()
                .name(masterDataDto.getName())
                .build();
        return departmentRepository.save(department);
    }

    public void deleteDpt(Long id) {
        Department department = departmentRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        int memberCount = memberRepository.countByDepartmentId(department.getId());
        departmentRepository.delete(department);
    }
}
