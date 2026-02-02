# Polling Set TTL 적용 개선 리포트

## 개선 일자
2026-01-30

## 문제 상황

### 기존 문제점
- `stt:polling:processing` (Redis Set) - TTL 없음
- `stt:polling:summarizing` (Redis Set) - TTL 없음
- 서버 비정상 종료 시 Set에서 제거되지 않은 항목이 영구 누적되는 문제 발생

### 위험성
- 메모리 누수: 비정상 종료된 작업들이 Redis에 계속 쌓임
- 불필요한 폴링: 스케줄러가 존재하지 않는 작업을 계속 폴링 시도
- 장기 운영 시 Redis 메모리 증가

---

## 해결 방안

### 선택된 방안: Sorted Set + 정리 스케줄러

| 방안 | 장점 | 단점 |
|------|------|------|
| **A. Sorted Set + 정리 스케줄러** | 기존 구조 유지, 조회 효율적 | 스케줄러 추가 필요 |
| B. 개별 String 키 + TTL | TTL 자동 관리, 단순함 | 조회 시 패턴 스캔 필요 (느림) |

**선택 이유:**
- Sorted Set은 폴링 작업 목록 전체 조회에 O(N) 복잡도로 효율적
- 패턴 스캔(KEYS/SCAN) 방식은 대규모 키에서 성능 저하
- score에 타임스탬프를 저장하여 오래된 항목만 선별 삭제 가능

---

## 구현 내용

### 1. 수정된 파일

**`src/main/java/com/codehows/daehobe/stt/service/cache/SttCacheService.java`**

| 메서드 | 변경 내용 |
|--------|----------|
| `addToPollingSet()` | `opsForSet()` → `opsForZSet()`, 타임스탬프를 score로 저장 |
| `removeFromPollingSet()` | `opsForSet()` → `opsForZSet()` |
| `getPollingTaskIds()` | `members()` → `range(0, -1)` |
| `cleanupStalePollingTasks()` | **신규** - 1시간 이상 된 항목 자동 정리 |
| `cleanupStaleTasksFromSet()` | **신규** - 특정 Set의 오래된 항목 삭제 |

### 2. 주요 코드 변경

**항목 추가 (변경 전)**
```java
hashRedisTemplate.opsForSet().add(setKey, String.valueOf(sttId));
```

**항목 추가 (변경 후)**
```java
double score = System.currentTimeMillis();
hashRedisTemplate.opsForZSet().add(setKey, String.valueOf(sttId), score);
```

**정리 스케줄러 (신규)**
```java
@Scheduled(fixedDelayString = "${stt.polling.cleanup-interval-ms:600000}")
public void cleanupStalePollingTasks() {
    long thresholdTime = System.currentTimeMillis() - (staleThresholdMinutes * 60 * 1000);
    cleanupStaleTasksFromSet(STT_POLLING_PROCESSING_SET, thresholdTime);
    cleanupStaleTasksFromSet(STT_POLLING_SUMMARIZING_SET, thresholdTime);
}

private void cleanupStaleTasksFromSet(String setKey, long thresholdTime) {
    Long removedCount = hashRedisTemplate.opsForZSet()
            .removeRangeByScore(setKey, 0, thresholdTime);
    if (removedCount != null && removedCount > 0) {
        log.info("Cleaned up {} stale polling tasks from {}", removedCount, setKey);
    }
}
```

### 3. 설정 옵션

**`application.properties`**
```properties
# STT Polling Settings
stt.polling.stale-threshold-minutes=60    # 오래된 항목 기준 시간 (기본 1시간)
stt.polling.cleanup-interval-ms=600000    # 정리 스케줄러 실행 간격 (기본 10분)
```

---

## 동작 방식

### Sorted Set 구조
```
Key: stt:polling:processing
+---------+-------------------+
| Member  | Score (timestamp) |
+---------+-------------------+
| "123"   | 1738234567890     |
| "456"   | 1738234600000     |
| "789"   | 1738234650000     |
+---------+-------------------+
```

### 정리 스케줄러 동작
1. 10분마다 스케줄러 실행
2. 현재 시간 - 1시간 = threshold 시간 계산
3. `ZREMRANGEBYSCORE` 명령으로 score < threshold인 항목 삭제
4. 삭제된 항목 수 로깅

### Redis 명령어 검증
```bash
# ZSet 구조 확인
ZRANGE stt:polling:processing 0 -1 WITHSCORES

# 특정 시간 이전 항목 조회
ZRANGEBYSCORE stt:polling:processing 0 <threshold_timestamp>

# 오래된 항목 삭제 (스케줄러가 수행하는 작업)
ZREMRANGEBYSCORE stt:polling:processing 0 <threshold_timestamp>
```

---

## 기대 효과

### 1. 메모리 누수 방지
- 서버 비정상 종료 시에도 최대 1시간 후 자동 정리
- Redis 메모리 사용량 안정화

### 2. 불필요한 폴링 감소
- 오래된 작업에 대한 폴링 시도 자동 제거
- 시스템 리소스 효율화

### 3. 운영 편의성
- 수동 정리 작업 불필요
- 설정 값으로 정리 주기 및 기준 시간 조절 가능

---

## 검증 결과

| 항목 | 결과 |
|------|------|
| 컴파일 | 성공 |
| 기존 테스트 | 통과 |
| 기능 호환성 | 유지 (API 변경 없음) |

---

## 향후 개선 가능 사항

1. **모니터링 메트릭 추가**: 정리된 항목 수를 Prometheus 메트릭으로 노출
2. **알림 설정**: 비정상적으로 많은 항목이 정리될 경우 알림 발송
3. **동적 설정**: 런타임에 threshold 값 변경 가능하도록 개선
