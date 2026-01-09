package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.masterData.Category;
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

    public Department getDepartmentById(Long id) {
        return id == null ? null :
                departmentRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("부서가 존재하지 않습니다."));
    }

    public List<MasterDataDto> findAll() {
        List<Department> dpts = departmentRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (Department dpt : dpts) {
            dtoList.add(new MasterDataDto(dpt.getId(), dpt.getName()));
        }
        return dtoList;
    }

    public Department createDpt(MasterDataDto masterDataDto) {
        String departmentName = masterDataDto.getName();

        // 중복 체크
        if (departmentRepository.existsByName(departmentName)) {
            throw new IllegalArgumentException("이미 존재하는 부서입니다: " + departmentName);
        }

        Department department = Department.builder()
                .name(departmentName)
                .build();

        try {
            return departmentRepository.save(department);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteDpt(Long id) {
        Department department = departmentRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        // 로직 추가.
        departmentRepository.delete(department);
    }

    public Department updateDpt(Long id, MasterDataDto masterDataDto) {
        Department department = departmentRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        department.changeName(masterDataDto.getName());
        return department;
    }
}
