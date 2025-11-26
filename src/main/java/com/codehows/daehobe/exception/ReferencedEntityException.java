package com.codehows.daehobe.exception;

import lombok.Getter;

/**
 * 삭제하려는 엔티티(부서, 직급, 카테고리)가 다른 활성 데이터에 의해 참조되고 있어 데이터 무결성 유지를 위해 작업을 수행할 수 없을 때 발생하는 예외.
 * HTTP 응답 코드 409 Conflict에 해당.
 */
@Getter
public class ReferencedEntityException extends RuntimeException {

    // 이 엔티티를 참조하고 있는 다른 데이터의 개수 (충돌을 일으키는 엔티티 수)
    private final int conflictCount;

    /**
     * @param message 사용자에게 보여줄 상세 메시지 (예: "직원 {N}명이 있어 삭제 불가")
     * @param conflictCount 참조하는 데이터의 개수 (충돌 수)
     */
    public ReferencedEntityException(String message, int conflictCount) {
        super(message);
        this.conflictCount = conflictCount;
    }
}