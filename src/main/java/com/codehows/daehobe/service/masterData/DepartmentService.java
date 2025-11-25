package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.Department;
import com.codehows.daehobe.repository.DepartmentRepository;
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

    public List<MasterDataDto> findAll() {
        List<Department> dpts = departmentRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (Department dpt : dpts) {
            dtoList.add(new MasterDataDto(dpt.getId(), dpt.getName()));
        }
        System.out.println(dtoList);
        return dtoList;
    }
}
