package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.exception.ReferencedEntityException;
import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.repository.DepartmentRepository;
import com.codehows.daehobe.repository.MemberRepository;
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

        if (memberCount > 0) {
            // 참조하는 엔티티가 있다면 커스텀 예외(ResourceInUseException)를 던진다.
            throw new ReferencedEntityException(
                    "해당 부서를 사용하는 직원 " + memberCount + "명이 있어 삭제할 수 없습니다.",
                    memberCount
            );
        }
        departmentRepository.delete(department);
    }
}
