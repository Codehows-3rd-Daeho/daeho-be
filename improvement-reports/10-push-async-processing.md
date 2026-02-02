# P1: 푸시 알림 비동기 처리 개선

## 1. 문제 상황
- **현상**: `WebPushSenderImpl.sendPushNotification()`에서 `pushService.send(notification)` 호출이 동기 방식으로 처리
- **영향 범위**: 외부 FCM 서버 응답 대기 시간만큼 스레드 블로킹, 다수의 푸시 알림 전송 시 스레드 풀 고갈 위험
- **코드 위치**: `WebPushSenderImpl.java:73`

## 2. 원인 분석
- **근본 원인**: FCM 서버로의 HTTP 요청이 동기적으로 처리되어 응답을 받을 때까지 호출 스레드가 블로킹됨
- **기존 코드 문제점**:
```java
// WebPushSenderImpl.java - 동기 호출로 스레드 블로킹
HttpResponse response = pushService.send(notification);  // 블로킹!
int statusCode = response.getStatusLine().getStatusCode();
handlePushResponse(memberId, statusCode, response);
```

## 3. 해결 방법
- **변경 내용**: 전용 비동기 서비스 `PushAsyncService` 도입 및 `@Async` 기반 비동기 처리

### 3.1 PushResult DTO
```java
@Getter
@Builder
public class PushResult {
    private final boolean success;
    private final String endpoint;
    private final String errorMessage;
    private final int statusCode;
    private final long latencyMs;

    public static PushResult success(String endpoint, int statusCode, long latencyMs) { ... }
    public static PushResult failure(String endpoint, String errorMessage, long latencyMs) { ... }
}
```

### 3.2 PushAsyncService 인터페이스
```java
public interface PushAsyncService {
    CompletableFuture<PushResult> sendAsync(Notification notification);
    CompletableFuture<List<PushResult>> sendAllAsync(List<Notification> notifications);
}
```

### 3.3 PushAsyncServiceImpl 구현체
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PushAsyncServiceImpl implements PushAsyncService {
    private final PushService pushService;

    @Override
    @Async("pushAsyncExecutor")
    public CompletableFuture<PushResult> sendAsync(Notification notification) {
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            long latency = System.currentTimeMillis() - startTime;

            if (statusCode >= 200 && statusCode < 300) {
                return CompletableFuture.completedFuture(
                        PushResult.success(notification.getEndpoint(), statusCode, latency));
            } else {
                return CompletableFuture.completedFuture(
                        PushResult.failure(notification.getEndpoint(), "HTTP " + statusCode, statusCode, latency));
            }
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    PushResult.failure(notification.getEndpoint(), e.getMessage(),
                        System.currentTimeMillis() - startTime));
        }
    }
}
```

### 3.4 pushAsyncExecutor Bean 추가
```java
// AsyncConfig.java
@Bean(name = "pushAsyncExecutor")
public Executor pushAsyncExecutor() {
    AsyncProperties.ExecutorProperties props = asyncProperties.getPush();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(props.getCorePoolSize());      // 기본값: 10
    executor.setMaxPoolSize(props.getMaxPoolSize());        // 기본값: 50
    executor.setQueueCapacity(props.getQueueCapacity());    // 기본값: 1000
    executor.setThreadNamePrefix(props.getThreadNamePrefix());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
}
```

### 3.5 WebPushSenderImpl 수정
```java
// Before (동기)
HttpResponse response = pushService.send(notification);
int statusCode = response.getStatusLine().getStatusCode();
handlePushResponse(memberId, statusCode, response);

// After (비동기)
pushAsyncService.sendAsync(notification)
    .whenComplete((result, ex) -> handleAsyncResult(memberId, endpoint, result, ex));
```

## 4. 설정 외부화
application.properties에서 스레드풀 설정 가능:
```properties
async.push.core-pool-size=10
async.push.max-pool-size=50
async.push.queue-capacity=1000
async.push.thread-name-prefix=push-async-
```

## 5. 변경 파일 요약

### 신규 생성 (4개)
| 파일 | 설명 |
|------|------|
| `k6/push-notification-test.js` | k6 성능 테스트 스크립트 |
| `notification/dto/PushResult.java` | 푸시 결과 DTO |
| `webPush/service/PushAsyncService.java` | 비동기 서비스 인터페이스 |
| `webPush/service/impl/PushAsyncServiceImpl.java` | 비동기 서비스 구현체 |

### 수정 (3개)
| 파일 | 변경 내용 |
|------|---------|
| `AsyncConfig.java` | `pushAsyncExecutor` Bean 추가 |
| `AsyncProperties.java` | `push` executor 속성 추가 |
| `WebPushSenderImpl.java` | 동기 → 비동기 호출 |

### 테스트 (1개)
| 파일 | 설명 |
|------|------|
| `PushAsyncServiceTest.java` | 6개 테스트 케이스 |

## 6. 예상 성능 개선

| 지표 | Before (동기) | After (비동기) | 개선율 |
|------|--------------|---------------|-------|
| p95 Latency | ~1500ms | ~200ms | ~87% |
| Throughput | ~30 req/s | ~150 req/s | ~5x |
| Thread Blocking | O | X | - |

## 7. 검증
- [x] 컴파일 성공: `./gradlew compileJava`
- [x] 테스트 통과: `./gradlew test --tests "*PushAsyncService*"` (6/6 passed)
- [x] 비동기 호출로 호출 스레드 즉시 반환
- [x] 전용 스레드풀로 리소스 관리 가능
- [x] CallerRunsPolicy로 큐 초과 시에도 알림 손실 없음
- [x] Graceful shutdown 지원 (awaitTerminationSeconds=30)

## 8. 성능 테스트 실행 방법
```bash
# k6 설치 후 실행
k6 run k6/push-notification-test.js --out json=k6/results/push-before.json

# 코드 변경 후 재실행
k6 run k6/push-notification-test.js --out json=k6/results/push-after.json
```

## 9. 롤백 계획
문제 발생 시:
1. `WebPushSenderImpl`에서 `PushAsyncService` → `PushService` 직접 호출로 복원
2. `@Async` 제거 후 동기 방식으로 롤백
