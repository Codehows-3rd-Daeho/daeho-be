package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.JobPosition;
import com.codehows.daehobe.repository.JobPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class JobPositionService {
    private final JobPositionRepository jobPositionRepository;

    public List<MasterDataDto> findAll() {
        List<JobPosition> jobPositions = jobPositionRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (JobPosition jobPosition : jobPositions) {
            dtoList.add(new MasterDataDto(jobPosition.getId(), jobPosition.getName()));
        }
        return dtoList;
    }
}
