# STT 폴링 최적화: Redis-First 아키텍처

## 트러블슈팅 및 성능 개선 리포트

**작성일**: 2026-01-30
**버전**: v2.2 (Redis-First Architecture - MySQL 실측 데이터 추가)

---

## 1. 문제 상황

### 1.1 현상

STT 처리 과정에서 PROCESSING → SUMMARIZING → COMPLETED 상태 전환 시 **반복적인 DB I/O**가 발생하여 다음과 같은 문제가 확인되었습니다:

**구체적 증상:**
- 폴링 스케줄러가 2초마다 DB 쿼리를 2회씩 실행 (PROCESSING, SUMMARIZING 상태 조회)
- 각 폴링 사이클마다 retry count 업데이트를 위한 추가 DB Write 발생
- 동시 STT 작업이 증가할수록 DB 부하가 선형적으로 증가
- DB 커넥션 풀 점유 시간 증가

**기존 코드 (SttPollingScheduler.java):**
```java
@Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
public void pollProcessingTasks() {
    // 문제: 2초마다 DB 쿼리 실행
    List<STT> tasks = sttRepository.findByStatusAndRetryCountLessThan(
            STT.Status.PROCESSING, maxAttempts);

    for (STT stt : tasks) {
        try {
            sttJobProcessor.processSingleSttJob(stt.getId());
        } catch (SttNotCompletedException e) {
            // 문제: 매번 DB Read + Write
            STT freshStt = sttRepository.findById(stt.getId()).orElse(null);
            freshStt.incrementRetryCount();
            sttRepository.save(freshStt);  // DB Write
        }
    }
}
```

### 1.2 문제의 영향

| 문제 | 영향 | 심각도 |
|------|------|--------|
| 2초마다 DB 폴링 쿼리 | DB 커넥션 점유, 쿼리 비용 누적 | 높음 |
| retry count DB 저장 | 불필요한 트랜잭션 오버헤드 | 중간 |
| PROCESSING/SUMMARIZING 상태 DB 저장 | 임시 상태의 불필요한 영속화 | 중간 |

---

## 2. 원인 분석

### 2.1 근본 원인

**폴링 대상 추적을 DB에 의존**하여 불필요한 I/O 발생:

```
┌─────────────────────────────────────────────────────────────────────┐
│  상태 전환 흐름 (개선 전)                                            │
├─────────────────────────────────────────────────────────────────────┤
│  RECORDING  → DB INSERT + Redis Cache                               │
│  ENCODING   → DB UPDATE + Redis Cache                               │
│  ENCODED    → Redis Cache Only (문제: DB 미저장)                     │
│  PROCESSING → DB UPDATE + Redis Cache ← 폴링 대상 DB 조회           │
│  SUMMARIZING→ DB UPDATE + Redis Cache ← 폴링 대상 DB 조회           │
│  COMPLETED  → DB UPDATE + Redis Cache                               │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 폴링 구간 DB I/O 상세

**STT 1건이 5분(300초) 동안 PROCESSING + SUMMARIZING 상태인 경우:**

| 작업 | 횟수 | 설명 |
|------|------|------|
| DB 폴링 쿼리 | 150회 | 2초마다 1회 × 150회 |
| retry count 업데이트 | 150회 | 폴링마다 DB Read + Write |
| **총 DB 작업** | **~300회** | 불필요한 I/O |

---

## 3. 해결 방법

### 3.1 Redis-First 아키텍처 도입

**핵심 전략:** 폴링 대상 추적을 Redis SET으로 이관하여 DB 의존성 제거

```
┌─────────────────────────────────────────────────────────────────────┐
│  상태 전환 흐름 (개선 후)                                            │
├─────────────────────────────────────────────────────────────────────┤
│  RECORDING  → DB INSERT + Redis Cache                               │
│  ENCODING   → DB UPDATE + Redis Cache                               │
│  ENCODED    → DB UPDATE + Redis Cache ← 추가 (복구 지점)             │
├──────────────── Redis-Only Zone ────────────────────────────────────┤
│  PROCESSING → Redis Cache + Redis SET (폴링 대상)                   │
│  SUMMARIZING→ Redis Cache + Redis SET (폴링 대상)                   │
├─────────────────────────────────────────────────────────────────────┤
│  COMPLETED  → DB UPDATE + Redis Cache (최종 저장)                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Redis 키 구조 설계

```
신규 Redis 키:
┌─────────────────────────────────────────────────────────────────────┐
│  [SET] stt:polling:processing     → {sttId1, sttId2, ...}          │
│  [SET] stt:polling:summarizing    → {sttId1, sttId2, ...}          │
│  [String] stt:retry:{sttId}       → "0", "1", "2", ... (TTL 30분)  │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.3 구현 변경사항

#### 3.3.1 SttCacheService.java - 폴링 SET 관리
```java
public void addToPollingSet(Long sttId, STT.Status status) {
    String setKey = getPollingSetKey(status);
    hashRedisTemplate.opsForSet().add(setKey, String.valueOf(sttId));
}

public Set<Long> getPollingTaskIds(STT.Status status) {
    String setKey = getPollingSetKey(status);
    Set<String> members = hashRedisTemplate.opsForSet().members(setKey);
    return members.stream().map(Long::valueOf).collect(Collectors.toSet());
}

public int incrementRetryCount(Long sttId) {
    String key = STT_RETRY_COUNT_PREFIX + sttId;
    Long newValue = hashRedisTemplate.opsForValue().increment(key);
    hashRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
    return newValue.intValue();
}
```

#### 3.3.2 SttPollingScheduler.java - Redis 기반 폴링
```java
@Scheduled(fixedDelayString = "${stt.polling.interval-ms:2000}")
public void pollProcessingTasks() {
    // 개선: Redis SET에서 폴링 대상 조회 (DB 쿼리 제거)
    Set<Long> taskIds = sttCacheService.getPollingTaskIds(STT.Status.PROCESSING);

    for (Long sttId : taskIds) {
        try {
            sttJobProcessor.processSingleSttJob(sttId);
        } catch (SttNotCompletedException e) {
            // 개선: Redis에서 retry count 관리 (DB Write 제거)
            int retryCount = sttCacheService.incrementRetryCount(sttId);
            if (retryCount >= maxAttempts) {
                handleMaxRetryExceeded(sttId, STT.Status.PROCESSING);
            }
        }
    }
}
```

---

## 4. 성능 개선 결과

### 4.1 테스트 환경

#### 4.1.1 기본 테스트 환경
- **Redis**: redis:7-alpine (Testcontainers)
- **DB**: H2 in-memory
- **측정 단위**: 마이크로초 (us)
- **테스트 조건**: 워밍업 50회 후 200회 측정

#### 4.1.2 실제 DB 테스트 환경 (운영 환경 시뮬레이션)
- **DB**: MySQL 8.0 (Testcontainers)
- **Redis**: redis:7-alpine (Testcontainers)
- **테스트 데이터**: **100만 건** 더미 데이터
- **인덱스**: `idx_status_retry (status, retry_count)`
- **측정 단위**: 마이크로초 (us)
- **테스트 조건**: 워밍업 10회 후 50회 측정

### 4.2 실제 MySQL 성능 테스트 결과 (100만 건)

#### 폴링 쿼리 성능 비교

| 항목 | avg(us) | p50(us) | p95(us) | p99(us) |
|------|---------|---------|---------|---------|
| DB 폴링 쿼리 (100만 건) | 4,181 | 4,120 | 5,198 | 5,972 |
| Redis SET 조회 (100만 건) | 1,558 | 1,447 | 2,703 | 4,306 |
| **개선율** | **62.7%↓** | **64.9%↓** | **48.0%↓** | **27.9%↓** |

#### 전체 폴링 사이클 성능 비교

| 항목 | avg(us) | p50(us) | p95(us) | p99(us) |
|------|---------|---------|---------|---------|
| 폴링 사이클 - DB (100만 건) | 114,549 | 104,176 | 170,529 | 250,046 |
| 폴링 사이클 - Redis (100만 건) | 93,621 | 86,558 | 134,804 | 154,303 |
| **개선율** | **18.3%↓** | **16.9%↓** | **21.0%↓** | **38.3%↓** |

### 4.3 기본 성능 테스트 결과 (H2)

#### 폴링 대상 조회 비교 (폴링 대상 10개)

| 항목 | 평균(us) | p95(us) | p99(us) |
|------|----------|---------|---------|
| DB `findByStatusAndRetryCountLessThan` | 1,090 | 2,049 | 3,318 |
| Redis `SMEMBERS` | 582 | 918 | 1,411 |
| **개선율** | **47%↓** | **55%↓** | **57%↓** |

#### 전체 성능 테스트 결과

```
==========================================================================================
                         STT 성능 테스트 결과 요약
==========================================================================================

[기본 성능 테스트 - 밀리초 단위]
테스트 항목                                        TPS    p50(ms)    p95(ms)    p99(ms)
------------------------------------------------------------------------------------------
Redis 캐시 쓰기 (단일)                           8,547       0.00       1.00       1.00
Redis 캐시 읽기 (단일)                          10,638       0.00       1.00       1.00
DB findByStatus                                   704       1.00       2.00      51.00
DB 폴링 쿼리                                       735       1.00       3.00       4.00

[핵심 비교 테스트 - 마이크로초 단위]
테스트 항목                                        TPS    avg(us)    p95(us)    p99(us)
------------------------------------------------------------------------------------------
폴링 조회 - DB (개선 전)                           917       1090       2049       3318
폴링 조회 - Redis (개선 후)                       1720        582        918       1411
==========================================================================================
```

### 4.4 개선 효과 분석

#### 4.4.1 레이턴시 개선 (MySQL 100만 건 기준)

| 지표 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| 폴링 쿼리 평균 | 4,181us | 1,558us | **62.7%↓** |
| 폴링 쿼리 p50 | 4,120us | 1,447us | **64.9%↓** |
| 폴링 사이클 평균 | 114.5ms | 93.6ms | **18.3%↓** |

#### 4.4.2 DB I/O 제거 (핵심 개선)

**STT 1건 완료 기준:**

| 지표 | 개선 전 | 개선 후 | 감소량 |
|------|---------|---------|--------|
| DB Write 횟수 | ~5회 | ~3회 | **2회↓** |
| 폴링 시 DB 쿼리 | 2회/2초 | 0회 | **100%↓** |

**5분 동안 처리되는 STT의 경우:**

| 지표 | 개선 전 | 개선 후 | 절감량 |
|------|---------|---------|--------|
| 폴링 DB 쿼리 | 150회 | 0회 | **150회** |
| retry count DB 업데이트 | 150회 | 0회 | **150회** |
| **총 DB 작업** | **~300회** | **0회** | **100%↓** |

### 4.5 테스트 결과 분석

#### 4.5.1 실측 데이터 의의

> **중요**: MySQL 8.0 + 100만 건 테스트는 실제 운영 환경에 가까운 조건에서 수행되었습니다.
>
> - **인덱스 탐색 비용 포함**: `idx_status_retry (status, retry_count)` 인덱스 사용
> - **대용량 테이블**: 100만 건 데이터로 인한 I/O 비용 반영
> - **실제 MySQL 8.0 엔진**: Testcontainers를 통한 실제 MySQL 8.0 사용

#### 4.5.2 개선율 분석

| 항목 | H2 테스트 | MySQL 100만 건 테스트 | 비고 |
|------|-----------|----------------------|------|
| 폴링 쿼리 개선율 | 47% | **62.7%** | 대용량에서 더 큰 개선 |
| 폴링 사이클 개선율 | - | **18.3%** | 전체 사이클 포함 |

> **해석**: 데이터가 많을수록 DB 쿼리 비용이 증가하여 Redis 전환 효과가 더 커집니다.
> 실제 운영 환경에서는 네트워크 오버헤드까지 추가되어 개선율이 더 높을 것으로 예상됩니다.

---

## 5. 아키텍처 다이어그램

### 5.1 Redis 키 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Redis 키 구조                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [String - 상태 캐시]                                                   │
│  stt:status:{sttId}                                                     │
│    └─ Value: JSON (STTDto)                                             │
│    └─ TTL: 상태별 동적 (RECORDING: 1h, ENCODED: 24h, COMPLETED: 10m)   │
│                                                                         │
│  [String - Heartbeat]                                                   │
│  stt:recording:heartbeat:{sttId}                                        │
│    └─ Value: ""                                                         │
│    └─ TTL: 30초                                                         │
│                                                                         │
│  [SET - 폴링 대상] ← 신규                                               │
│  stt:polling:processing                                                 │
│    └─ Members: {sttId1, sttId2, ...}                                   │
│  stt:polling:summarizing                                                │
│    └─ Members: {sttId1, sttId2, ...}                                   │
│                                                                         │
│  [String - Retry Count] ← 신규                                          │
│  stt:retry:{sttId}                                                      │
│    └─ Value: "0", "1", "2", ...                                        │
│    └─ TTL: 30분                                                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 상태 전환 및 DB 저장 시점

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [RECORDING]                                                            │
│    └─ DB: INSERT stt (status=RECORDING)                                 │
│    └─ Redis: cache status + heartbeat                                   │
│                              │                                          │
│                              ▼                                          │
│  [ENCODING]                                                             │
│    └─ DB: UPDATE stt (status=ENCODING)                                  │
│    └─ Redis: cache status                                               │
│                              │                                          │
│                              ▼                                          │
│  [ENCODED]  ◀── 복구 지점 (서버 재시작 시 여기서 재개 가능)              │
│    └─ DB: UPDATE stt (status=ENCODED)                                   │
│    └─ Redis: cache status (24h TTL)                                     │
│                              │                                          │
│                              ▼ (사용자 요청)                             │
├─────────────────────── Redis-Only Zone ────────────────────────────────┤
│  [PROCESSING]                                                           │
│    └─ DB: 없음                                                          │
│    └─ Redis: cache status + SADD stt:polling:processing                │
│                              │                                          │
│                              ▼ (폴링 완료)                               │
│  [SUMMARIZING]                                                          │
│    └─ DB: 없음                                                          │
│    └─ Redis: cache status + SMOVE polling set                          │
│                              │                                          │
│                              ▼ (폴링 완료)                               │
├─────────────────────────────────────────────────────────────────────────┤
│  [COMPLETED]                                                            │
│    └─ DB: UPDATE stt (모든 필드 최종 저장)                               │
│    └─ Redis: cache status + SREM from polling set                      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. 복구 전략

### 6.1 서버 재시작 시

Redis polling SET에 진행 중인 작업이 남아 있으면 자동으로 폴링 재개:

```java
@PostConstruct
public void recoverPendingTasks() {
    Set<Long> processingIds = sttCacheService.getPollingTaskIds(PROCESSING);
    Set<Long> summarizingIds = sttCacheService.getPollingTaskIds(SUMMARIZING);
    log.info("Recovered {} processing, {} summarizing tasks",
             processingIds.size(), summarizingIds.size());
}
```

### 6.2 Redis 재시작 시

- DB에서 ENCODED 상태인 STT 조회 가능 (복구 지점 보장)
- 사용자가 다시 변환 요청하면 PROCESSING부터 재시작
- 진행 중이던 PROCESSING/SUMMARIZING은 유실 → 사용자에게 재시도 안내

### 6.3 Max Retry 초과 시

```java
private void handleMaxRetryExceeded(Long sttId, STT.Status currentStatus) {
    sttCacheService.removeFromPollingSet(sttId, currentStatus);
    sttCacheService.resetRetryCount(sttId);

    // ENCODED 상태로 롤백 (사용자가 재시도 가능)
    STTDto cachedStatus = sttCacheService.getCachedSttStatus(sttId);
    if (cachedStatus != null) {
        cachedStatus.updateStatus(STT.Status.ENCODED);
        sttCacheService.cacheSttStatus(cachedStatus);
    }
}
```

---

## 7. 변경 파일 목록

| 파일 | 변경 유형 | 설명 |
|------|-----------|------|
| `SttRedisKeys.java` | 수정 | 폴링 SET 키 상수 추가 |
| `STTDto.java` | 수정 | retryCount 필드 및 메서드 추가 |
| `SttCacheService.java` | 수정 | 폴링 SET 관리 메서드 추가 |
| `SttJobProcessor.java` | 수정 | ENCODED DB 저장, SUMMARIZING 전환 로직 변경 |
| `STTService.java` | 수정 | startTranslateForRecorded DB 저장 제거 |
| `SttPollingScheduler.java` | 수정 | Redis SET 기반 폴링으로 전체 리팩토링 |
| `SttPerformanceTest.java` | 수정 | 현실적인 비교 테스트 추가 |

---

## 8. 결론

### 8.1 달성 목표

| 목표 | 상태 | 비고 |
|------|------|------|
| PROCESSING/SUMMARIZING 폴링 구간 DB I/O 제거 | 완료 | Redis SET 기반 구현 |
| Redis SET 기반 폴링 대상 추적 | 완료 | O(1) 추가/제거, O(N) 조회 |
| Redis 기반 retry count 관리 | 완료 | INCR 명령어 사용 |
| ENCODED 상태 DB 저장 추가 | 완료 | 복구 지점 보장 |
| 기존 테스트 케이스 통과 | 완료 | 전체 테스트 PASS |

### 8.2 성능 개선 요약

#### MySQL 8.0 + 100만 건 실측 결과

| 카테고리 | 개선 전 | 개선 후 | 개선율 |
|----------|---------|---------|--------|
| 폴링 쿼리 레이턴시 (avg) | 4,181us | 1,558us | **62.7%↓** |
| 폴링 쿼리 레이턴시 (p50) | 4,120us | 1,447us | **64.9%↓** |
| 폴링 사이클 레이턴시 (avg) | 114.5ms | 93.6ms | **18.3%↓** |
| 폴링 시 DB 쿼리 | 2회/2초 | 0회 | **100%↓** |
| STT 1건당 DB Write | ~5회 | ~3회 | **40%↓** |

### 8.3 핵심 개선 포인트

> **가장 중요한 개선**: 폴링 구간에서 **DB 쿼리 자체를 제거**한 것입니다.
>
> - 레이턴시 **62.7%** 개선 (100만 건 실측 기준)
> - **DB 커넥션 점유 시간 0으로 감소**
> - **폴링 빈도에 비례하던 DB 부하 완전 제거**
> - **DB 장애 시에도 폴링 계속 동작 가능**
> - 대용량 데이터에서 더 큰 개선 효과 확인

---

*문서 버전: 2.2*
*최종 수정: 2026-01-30*
*MySQL 8.0 + 100만 건 실측 데이터 포함*
