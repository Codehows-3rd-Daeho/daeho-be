package com.codehows.daehobe.logging.AOP.interfaces;

import com.codehows.daehobe.common.constant.TargetType;

/**
 * 댓글과 같이 부모 엔티티를 가지는 엔티티의 로그 기록 시,
 * 부모 정보를 타입-세이프하게 제공하기 위한 인터페이스입니다.
 */
public interface CommentLogInfoProvider {

    /**
     * 부모 엔티티의 ID를 반환합니다.
     * @return 부모 엔티티 ID
     */
    Long getParentTargetId();

    /**
     * 부모 엔티티의 타입을 반환합니다.
     * @return 부모 엔티티 타입
     */
    TargetType getParentTargetType();
}
