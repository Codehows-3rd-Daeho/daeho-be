package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.JobPosition;
import com.codehows.daehobe.masterData.repository.JobPositionRepository;
import com.codehows.daehobe.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class JobPositionServiceTest {

    @Mock
    private JobPositionRepository jobPositionRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private JobPositionService jobPositionService;

    @Test
    @DisplayName("성공: 모든 직급 조회")
    void findAll_Success() {
        // given
        when(jobPositionRepository.findAll()).thenReturn(Collections.singletonList(
                JobPosition.builder().id(1L).name("사원").build()
        ));

        // when
        List<MasterDataDto> result = jobPositionService.findAll();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("사원");
    }

    @Test
    @DisplayName("성공: 직급 생성")
    void createPos_Success() {
        // given
        MasterDataDto dto = new MasterDataDto(null, "대리");
        when(jobPositionRepository.existsByName("대리")).thenReturn(false);
        when(jobPositionRepository.save(any(JobPosition.class)))
                .thenReturn(JobPosition.builder().id(2L).name("대리").build());

        // when
        JobPosition result = jobPositionService.createPos(dto);

        // then
        assertThat(result.getName()).isEqualTo("대리");
        verify(jobPositionRepository).save(any(JobPosition.class));
    }

    @Test
    @DisplayName("실패: 중복 직급 생성")
    void createPos_Duplicate() {
        // given
        MasterDataDto dto = new MasterDataDto(null, "사원");
        when(jobPositionRepository.existsByName("사원")).thenReturn(true);

        // when & then
        assertThrows(IllegalArgumentException.class, () -> jobPositionService.createPos(dto));
        verify(jobPositionRepository, never()).save(any(JobPosition.class));
    }

    @Test
    @DisplayName("성공: 직급 삭제")
    void deletePos_Success() {
        // given
        Long posId = 1L;
        JobPosition position = JobPosition.builder().id(posId).name("삭제할직급").build();
        when(jobPositionRepository.findById(posId)).thenReturn(Optional.of(position));
        when(memberRepository.countByJobPositionId(posId)).thenReturn(0);

        // when
        jobPositionService.deletePos(posId);

        // then
        verify(jobPositionRepository).delete(position);
    }

    @Test
    @DisplayName("실패: 삭제할 직급 없음")
    void deletePos_NotFound() {
        // given
        Long posId = 99L;
        when(jobPositionRepository.findById(posId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> jobPositionService.deletePos(posId));
    }

    @Test
    @DisplayName("성공: 직급 업데이트")
    void updatePos_Success() {
        // given
        Long posId = 1L;
        MasterDataDto dto = new MasterDataDto(posId, "수정된직급");
        JobPosition position = JobPosition.builder().id(posId).name("원본직급").build();
        when(jobPositionRepository.findById(posId)).thenReturn(Optional.of(position));

        // when
        JobPosition result = jobPositionService.updatePos(posId, dto);

        // then
        assertThat(result.getName()).isEqualTo("수정된직급");
    }

    @Test
    @DisplayName("실패: 업데이트할 직급 없음")
    void updatePos_NotFound() {
        // given
        Long posId = 99L;
        MasterDataDto dto = new MasterDataDto(posId, "수정된직급");
        when(jobPositionRepository.findById(posId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> jobPositionService.updatePos(posId, dto));
    }
}
