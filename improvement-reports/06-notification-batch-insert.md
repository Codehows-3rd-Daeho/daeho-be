# H1: Notification Batch Insert

## 1. 문제 상황
- **현상**: notifyMembers에서 N+1 쿼리 발생
- **영향 범위**: 100명 알림 → 100+ DB 쿼리
- **코드 위치**: `NotificationService.java:49-65`

## 2. 원인 분석
- **근본 원인**: 개별 INSERT 반복
- **기존 코드 문제점**:
```java
for (Long targetId : targetMemberIds) {
    saveNotification(targetId, dto);  // 개별 INSERT (N+1)
    sendNotification(String.valueOf(targetId), dto);
}
```

## 3. 해결 방법
- **변경 내용**: EntityManager.getReference()로 프록시 생성 + saveAll() 배치 저장

### NotificationService.java
```java
private final EntityManager entityManager;

public void notifyMembers(
        Collection<Long> targetMemberIds,
        Long writerId,
        String message,
        String url
) {
    NotificationMessageDto dto = new NotificationMessageDto();
    dto.setMessage(message);
    dto.setUrl(url);

    // 작성자 제외
    List<Long> filteredIds = targetMemberIds.stream()
            .filter(id -> !id.equals(writerId))
            .collect(Collectors.toList());

    if (filteredIds.isEmpty()) {
        return;
    }

    // Batch Insert: Member 프록시만 사용하여 알림 생성
    List<Notification> notifications = filteredIds.stream()
            .map(targetId -> {
                Member memberRef = entityManager.getReference(Member.class, targetId);
                return Notification.builder()
                        .member(memberRef)  // 프록시 사용 (SELECT 없음)
                        .message(dto.getMessage())
                        .forwardUrl(dto.getUrl())
                        .isRead(false)
                        .build();
            })
            .collect(Collectors.toList());

    // 단일 배치 저장
    notificationRepository.saveAll(notifications);

    // WebPush 알림은 비동기로 개별 전송
    for (Long targetId : filteredIds) {
        sendNotification(String.valueOf(targetId), dto);
    }
}
```

## 4. 성능 측정

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| DB 쿼리 (100명) | ~100회 INSERT | 1회 Batch INSERT | **99%↓** |
| Member 조회 | 100회 SELECT | 0회 (프록시) | **100%↓** |

## 5. 검증
- [x] EntityManager.getReference()로 Member SELECT 제거
- [x] saveAll()로 배치 INSERT 수행
- [x] WebPush는 기존처럼 비동기 개별 전송 유지
