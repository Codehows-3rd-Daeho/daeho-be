# C4: Redis Fallback 로직 추가

## 1. 문제 상황
- **현상**: Redis 장애 시 폴링 완전 중단
- **영향 범위**: 전체 STT 작업 처리 정지
- **코드 위치**: `SttPollingScheduler.java:30-48`, `SttCacheService.java`

## 2. 원인 분석
- **근본 원인**: Redis 의존성 100%, Fallback 미구현
- **기존 코드 문제점**:
```java
Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);
// Redis 장애 → 빈 Set 반환 → 폴링 안 됨 → 작업 정지
```

## 3. 해결 방법
- **변경 내용**: Redis 장애 감지 및 DB Fallback 구현

### STTRepository.java
```java
@Query("SELECT s.id FROM STT s WHERE s.status = :status")
Set<Long> findIdsByStatus(@Param("status") STT.Status status);
```

### SttCacheService.java
```java
public Set<Long> getPollingTaskIds(STT.Status status) {
    String setKey = getPollingSetKey(status);
    if (setKey == null) {
        return Collections.emptySet();
    }

    try {
        Set<String> members = hashRedisTemplate.opsForSet().members(setKey);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    } catch (Exception e) {
        log.warn("Redis unavailable for polling set {}. Exception: {}", setKey, e.getMessage());
        return Collections.emptySet();
    }
}

public boolean isRedisAvailable() {
    try {
        hashRedisTemplate.hasKey("health-check");
        return true;
    } catch (Exception e) {
        log.warn("Redis health check failed: {}", e.getMessage());
        return false;
    }
}
```

### SttPollingScheduler.java
```java
private Set<Long> getTaskIdsWithFallback(STT.Status status) {
    Set<Long> taskIds = sttCacheService.getPollingTaskIds(status);

    if (taskIds.isEmpty() && !sttCacheService.isRedisAvailable()) {
        try {
            taskIds = sttRepository.findIdsByStatus(status);
            log.warn("Redis unavailable, falling back to DB for {} polling. Found {} tasks",
                    status, taskIds.size());
        } catch (Exception e) {
            log.error("Both Redis and DB unavailable for polling", e);
        }
    }

    return taskIds;
}
```

## 4. 검증
- [x] Redis 정상 시 기존 동작 유지
- [x] Redis 장애 시 DB에서 작업 목록 조회
- [x] 양쪽 모두 장애 시 로그 기록 및 안전한 빈 Set 반환
