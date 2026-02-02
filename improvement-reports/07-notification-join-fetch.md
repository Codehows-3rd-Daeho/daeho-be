# H2: JOIN FETCH 쿼리 최적화

## 1. 문제 상황
- **현상**: getMyNotifications에서 N+1 쿼리 발생
- **영향 범위**: 페이지당 N+1 쿼리 (5개 알림 → 6 쿼리)
- **코드 위치**: `NotificationService.java:67-78`

## 2. 원인 분석
- **근본 원인**: 각 알림마다 발신자(createdBy) 조회
- **기존 코드 문제점**:
```java
Page<Notification> notifications = notificationRepository.findByMemberId(memberId, pageable);
return notifications.map(notification -> {
    Member sender = memberService.getMemberById(notification.getCreatedBy());  // N+1!
    return NotificationResponseDto.fromEntity(notification, sender);
});
```

## 3. 해결 방법
- **변경 내용**: Notification 엔티티에 createdByMember 연관관계 추가 + JOIN FETCH 쿼리

### Notification.java
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by", insertable = false, updatable = false)
private Member createdByMember;  // BaseEntity.createdBy와 매핑
```

### NotificationRepository.java
```java
@Query(value = "SELECT n FROM Notification n " +
        "LEFT JOIN FETCH n.createdByMember " +
        "WHERE n.member.id = :memberId",
        countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.member.id = :memberId")
Page<Notification> findByMemberIdWithCreatedByMember(@Param("memberId") Long memberId, Pageable pageable);
```

### NotificationResponseDto.java
```java
public static NotificationResponseDto fromEntityWithCreatedBy(Notification entity) {
    Member sender = entity.getCreatedByMember();
    String senderName = sender != null ? sender.getName() : "시스템";
    return NotificationResponseDto.builder()
            .id(entity.getId())
            .senderName(senderName)
            .message(entity.getMessage())
            .forwardUrl(entity.getForwardUrl())
            .read(entity.getIsRead())
            .createdAt(entity.getCreatedAt())
            .build();
}
```

### NotificationService.java
```java
public Page<NotificationResponseDto> getMyNotifications(Long memberId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Notification> notifications = notificationRepository.findByMemberIdWithCreatedByMember(memberId, pageable);
    return notifications.map(NotificationResponseDto::fromEntityWithCreatedBy);  // JOIN FETCH 활용
}
```

## 4. 성능 측정

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| DB 쿼리 (5개 알림) | 6회 | 2회 | **67%↓** |
| DB 쿼리 (10개 알림) | 11회 | 2회 | **82%↓** |
| DB 쿼리 (20개 알림) | 21회 | 2회 | **90%↓** |

※ 2회 = 1회 (알림 + 발신자 JOIN FETCH) + 1회 (COUNT)

## 5. 검증
- [x] JOIN FETCH로 단일 쿼리에서 발신자 정보 조회
- [x] 페이지네이션을 위한 COUNT 쿼리 분리
- [x] createdByMember가 null인 경우 "시스템" 처리
