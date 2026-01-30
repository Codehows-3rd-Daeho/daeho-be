# STT & Notification 모듈 성능 및 안정성 개선

## 개요
**목표**: STT와 Notification 모듈의 성능 개선 및 Fallback 처리 강화
**구현 일자**: 2026-01-30

---

## 구현 완료 항목

### Phase 1: 안정성 확보

| # | 이슈 | 파일 | 상태 |
|---|------|------|------|
| C1 | [Notification Async Executor 설정](./01-notification-async-executor.md) | AsyncConfig.java, NotificationService.java | ✅ 완료 |
| C2 | [WebPush 재시도 및 DLQ 추가](./02-webpush-retry-dlq.md) | WebPushService.java, RetryConfig.java | ✅ 완료 |
| C4 | [Redis Fallback 로직 추가](./03-redis-fallback.md) | SttCacheService.java, SttPollingScheduler.java | ✅ 완료 |

### Phase 2: 장애 대응 강화

| # | 이슈 | 파일 | 상태 |
|---|------|------|------|
| C3 | [Daglo API Circuit Breaker 적용](./04-circuit-breaker.md) | DagloSttProvider.java, Resilience4jConfig.java | ✅ 완료 |
| H4 | [WebClient 커넥션 풀 설정](./05-connection-pool.md) | WebClientConfig.java | ✅ 완료 |

### Phase 3: 성능 최적화

| # | 이슈 | 파일 | 상태 |
|---|------|------|------|
| H1 | [Notification Batch Insert](./06-notification-batch-insert.md) | NotificationService.java | ✅ 완료 |
| H2 | [JOIN FETCH 쿼리 최적화](./07-notification-join-fetch.md) | NotificationRepository.java, Notification.java | ✅ 완료 |
| H5 | [구독 유효성 검증](./08-subscription-validation.md) | WebPushService.java | ✅ 완료 |

### Phase 4: 운영 편의성

| # | 이슈 | 파일 | 상태 |
|---|------|------|------|
| M2 | [Async 설정 외부화](./09-async-config-externalize.md) | AsyncConfig.java, AsyncProperties.java | ✅ 완료 |
| M4 | [녹음 세션 타임아웃](./10-recording-session-timeout.md) | SttRecordingTimeoutScheduler.java | ✅ 완료 |

---

## 신규 파일 목록

```
src/main/java/com/codehows/daehobe/
├── config/
│   ├── AsyncProperties.java          (M2)
│   ├── Resilience4jConfig.java        (C3)
│   └── RetryConfig.java               (C2)
├── notification/
│   ├── dto/
│   │   └── FailedNotificationDto.java (C2)
│   └── exception/
│       └── InvalidSubscriptionException.java (H5)
└── stt/
    └── service/scheduler/
        └── SttRecordingTimeoutScheduler.java (M4)
```

---

## 추가된 의존성 (build.gradle)

```groovy
// Spring Retry
implementation 'org.springframework.retry:spring-retry'
implementation 'org.springframework:spring-aspects'

// Resilience4j Circuit Breaker
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
```

---

## 추가된 설정 (application.properties)

```properties
# Async Thread Pool Settings
async.stt.core-pool-size=2
async.stt.max-pool-size=4
async.stt.queue-capacity=100
async.stt.thread-name-prefix=stt-task-

async.notification.core-pool-size=4
async.notification.max-pool-size=8
async.notification.queue-capacity=500
async.notification.thread-name-prefix=notification-

# Recording Timeout Settings
stt.recording.timeout-check-interval-ms=30000
```

---

## 예상 효과

| 카테고리 | Before | After | 개선율 |
|----------|--------|-------|--------|
| Notification DB 쿼리 (100명) | ~100회 | ~2회 | **98%↓** |
| WebPush 실패 복구율 | 0% | ~90% | **90%↑** |
| Redis 장애 시 가용성 | 0% | ~80% | **80%↑** |
| Daglo API 장애 전파 차단 | 없음 | 있음 | **완전 차단** |
| 고아 세션 처리 | 수동 | 자동 | **자동화** |

---

## 검증 방법

```bash
# 전체 테스트 실행
./gradlew test

# 빌드 확인
./gradlew build
```
