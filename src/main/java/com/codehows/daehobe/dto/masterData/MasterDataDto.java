package com.codehows.daehobe.dto.masterData;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 직급 부서 카테고리 파일용량 파일확장자 (알림설정?)
@Getter
@AllArgsConstructor
public class MasterDataDto {
    private Long id;
    private String name;
}
