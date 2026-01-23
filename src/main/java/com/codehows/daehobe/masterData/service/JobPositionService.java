package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.repository.JobPositionRepository;
import com.codehows.daehobe.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
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
    private final MemberRepository memberRepository;

    public JobPosition getJobPositionById(Long id) {
        return id == null ? null :
                jobPositionRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("직급이 존재하지 않습니다."));
    }

    public List<MasterDataDto> findAll() {
        List<JobPosition> jobPositions = jobPositionRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (JobPosition jobPosition : jobPositions) {
            dtoList.add(new MasterDataDto(jobPosition.getId(), jobPosition.getName()));
        }
        return dtoList;
    }

    public JobPosition createPos(MasterDataDto masterDataDto) {
        String positionName = masterDataDto.getName();

        // 1. 중복 체크
        if (jobPositionRepository.existsByName(positionName)) {
            throw new IllegalArgumentException("이미 존재하는 직급입니다: " + positionName);
        }

        JobPosition position = JobPosition.builder()
                .name(positionName)
                .build();

        try {
            return jobPositionRepository.save(position);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void deletePos(Long id) {
        JobPosition jobPosition = jobPositionRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        // 참조하는 Member가 있는지 확인
        // JobPosition ID를 사용하여 해당 직급을 참조하는 Member 수를 카운트
        int memberCount = memberRepository.countByJobPositionId(jobPosition.getId());
        jobPositionRepository.delete(jobPosition);
    }

    public JobPosition updatePos(Long id, MasterDataDto masterDataDto) {
        JobPosition jobPosition = jobPositionRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        jobPosition.changeName(masterDataDto.getName());
        return jobPosition;
    }
}
