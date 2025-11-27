package com.codehows.daehobe.service.masterData;

import com.codehows.daehobe.exception.ReferencedEntityException;
import com.codehows.daehobe.dto.masterData.MasterDataDto;
import com.codehows.daehobe.entity.JobPosition;
import com.codehows.daehobe.repository.JobPositionRepository;
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
public class JobPositionService {
    private final JobPositionRepository jobPositionRepository;
    private final MemberRepository memberRepository;

    public List<MasterDataDto> findAll() {
        List<JobPosition> jobPositions = jobPositionRepository.findAll();
        List<MasterDataDto> dtoList = new ArrayList<>();
        for (JobPosition jobPosition : jobPositions) {
            dtoList.add(new MasterDataDto(jobPosition.getId(), jobPosition.getName()));
        }
        return dtoList;
    }

    public Long createPos(MasterDataDto masterDataDto) {
        JobPosition position = JobPosition.builder()
                .name(masterDataDto.getName())
                .build();
        jobPositionRepository.save(position);
        return position.getId();
    }

    public void deletePos(Long id) {
        JobPosition jobPosition = jobPositionRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        // 참조하는 Member가 있는지 확인
        // JobPosition ID를 사용하여 해당 직급을 참조하는 Member 수를 카운트
        int memberCount = memberRepository.countByJobPositionId(jobPosition.getId());

        if (memberCount > 0) {
            // 참조하는 엔티티가 있다면 커스텀 예외(DependentDataException)를 던진다.
            throw new ReferencedEntityException(
                    "해당 직급을 사용하는 직원 " + memberCount + "명이 있어 삭제할 수 없습니다.",
                    memberCount
            );
        }
        jobPositionRepository.delete(jobPosition);
    }
}
