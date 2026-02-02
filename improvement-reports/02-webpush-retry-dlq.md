# C2: WebPush 재시도 및 DLQ 추가 (v2 - 책임 분리 및 Jitter 적용)

## 1. 문제 상황
- **현상**: WebPush 실패 시 재시도 없이 알림 영구 손실
- **영향 범위**: 일시적 네트워크 오류에도 알림 전송 실패, 추적 불가
- **추가 문제**: 단일 클래스에 구독 관리, 푸시 전송, DLQ 관리가 혼재 (SRP 위반)

## 2. 원인 분석
- **근본 원인**: 실패 처리 로직 미흡 및 책임 분리 부재
- **기존 코드 문제점**:
```java
// WebPushService.java - 모든 책임이 한 클래스에 집중
public class WebPushService {
    // 1. 구독 저장/검증 (validateSubscription, saveSubscription)
    // 2. 푸시 전송 (sendNotificationToUser)
    // 3. DLQ 관리 (saveToDeadLetterQueue, getDlqSize)
    // → 단일 책임 원칙 위반
}
```

## 3. 해결 방법

### 3.1 클래스 책임 분리

| 클래스 | 책임 |
|--------|------|
| `PushSubscriptionService` | 구독 저장, 삭제, 조회, 유효성 검증 |
| `WebPushSenderImpl` | 순수 푸시 알림 전송 |
| `WebPushService` | Facade - 재시도 조율, 서비스 간 연계 |
| `NotificationDlqService` | DLQ 저장, 배치 재처리, 스케줄링 |

### 3.2 Jitter 적용 Retry (서버 부하 분산)

#### RetryConfig.java
```java
@Configuration
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryTemplate webPushRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Exponential Random Backoff Policy (Jitter 포함)
        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);  // 초기 대기 시간: 1초
        backOffPolicy.setMultiplier(2.0);          // 배수: 2배씩 증가
        backOffPolicy.setMaxInterval(10000L);      // 최대 대기 시간: 10초
        // ExponentialRandomBackOffPolicy는 자동으로 0~interval 사이의 random jitter 적용
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 재시도 대상 예외 설정
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(IOException.class, true);
        retryableExceptions.put(GeneralSecurityException.class, true);
        retryableExceptions.put(JoseException.class, true);
        retryableExceptions.put(RuntimeException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}
```

### 3.3 WebPushService (Facade)
```java
@Service
public class WebPushService {
    private final PushSubscriptionService subscriptionService;
    private final NotificationDlqService dlqService;
    private final WebPushSender webPushSender;
    private final RetryTemplate retryTemplate;

    public void sendNotificationToUser(String memberId, NotificationMessageDto messageDto) {
        Optional<PushSubscriptionDto> subscriptionOpt = subscriptionService.getSubscription(memberId);
        if (subscriptionOpt.isEmpty()) {
            return;
        }

        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("Retry attempt {} for member {}", context.getRetryCount(), memberId);
                }
                webPushSender.sendPushNotification(memberId, messageDto);
                return null;
            });
        } catch (Exception e) {
            log.error("All retry attempts failed for member {}. Moving to DLQ.", memberId, e);
            int statusCode = (e instanceof PushNotificationException)
                    ? ((PushNotificationException) e).getStatusCode() : 0;
            dlqService.saveToDeadLetterQueue(memberId, messageDto, statusCode, e.getMessage());
        }
    }
}
```

### 3.4 DLQ 배치 재처리 스케줄러 (단일 인스턴스)

#### NotificationDlqService.java
```java
@Service
public class NotificationDlqService {
    private static final String NOTIFICATION_DLQ_KEY = "notification:dlq";
    private static final int MAX_RETRY_COUNT = 5;

    @Value("${notification.dlq.batch-size:50}")
    private int batchSize;

    @Value("${notification.dlq.enabled:true}")
    private boolean dlqProcessingEnabled;

    /**
     * DLQ 배치 재처리 스케줄러
     * 5분마다 실행하며, DLQ에 있는 실패한 알림들을 배치로 재처리
     */
    @Scheduled(fixedDelayString = "${notification.dlq.process-interval:300000}")
    public void processDlqBatch() {
        if (!dlqProcessingEnabled) return;

        Long queueSize = getDlqSize();
        if (queueSize == null || queueSize == 0) return;

        log.info("Starting DLQ batch processing. Queue size: {}", queueSize);

        int itemsToProcess = (int) Math.min(batchSize, queueSize);
        for (int i = 0; i < itemsToProcess; i++) {
            Object rawItem = redisTemplate.opsForList().leftPop(NOTIFICATION_DLQ_KEY);
            if (rawItem == null) break;

            FailedNotificationDto failedNotification = parseItem(rawItem);

            if (shouldRetry(failedNotification)) {
                boolean success = retryNotification(failedNotification);
                if (!success) {
                    // 재시도 실패 시 retryCount 증가 후 다시 DLQ에 저장
                    saveToDeadLetterQueue(failedNotification.withIncrementedRetryCount());
                }
            } else {
                // 최대 재시도 횟수 초과 - 영구 실패 처리
                log.warn("Max retries exceeded for member {}", failedNotification.getMemberId());
            }
        }
    }

    private boolean shouldRetry(FailedNotificationDto notification) {
        // 410 (구독 만료)는 재시도하지 않음
        if (notification.getStatusCode() == 410) return false;
        return notification.getRetryCount() < MAX_RETRY_COUNT;
    }
}
```

### 3.5 FailedNotificationDto
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedNotificationDto {
    private String memberId;
    private String message;
    private String url;
    private int statusCode;
    private String errorMessage;
    private LocalDateTime failedAt;
    private int retryCount;

    public static FailedNotificationDto of(String memberId, NotificationMessageDto messageDto,
                                           int statusCode, String errorMessage) {
        return FailedNotificationDto.builder()
                .memberId(memberId)
                .message(messageDto.getMessage())
                .url(messageDto.getUrl())
                .statusCode(statusCode)
                .errorMessage(errorMessage)
                .failedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    public FailedNotificationDto withIncrementedRetryCount() {
        return FailedNotificationDto.builder()
                .memberId(this.memberId)
                .message(this.message)
                .url(this.url)
                .statusCode(this.statusCode)
                .errorMessage(this.errorMessage)
                .failedAt(this.failedAt)
                .retryCount(this.retryCount + 1)
                .build();
    }
}
```

## 4. 설정 옵션 (application.yml)
```yaml
notification:
  subscription:
    ttl-days: 7                # 구독 정보 TTL (기본 7일, 재구독 시 갱신)
  dlq:
    batch-size: 50             # 한 번에 처리할 최대 항목 수
    process-interval: 300000   # 배치 처리 주기 (밀리초, 기본 5분)
```

## 5. 아키텍처 개선 요약

### Before (단일 클래스)
```
WebPushService
├── 구독 저장/검증
├── 푸시 전송
├── 응답 처리
└── DLQ 관리
```

### After (책임 분리)
```
PushSubscriptionService (String 키 + TTL 7일)
├── saveSubscription()      # TTL 자동 적용
├── getSubscription()
├── deleteSubscription()
├── refreshSubscriptionTtl() # 사용자 활동 시 TTL 갱신
└── validateSubscription()

WebPushSenderImpl (implements WebPushSender)
├── sendPushNotification()
├── doSendPushNotification()
└── handlePushResponse()

WebPushService (Facade)
├── sendNotificationToUser() [with Jitter retry]
└── saveSubscription() [위임]

NotificationDlqService
├── saveToDeadLetterQueue()
├── processDlqBatch() [@Scheduled]
├── getDlqSize()
└── shouldRetry()
```

## 6. 검증
- [x] 일시적 오류 시 최대 3회 재시도 (Jitter 적용 - 1~10초 랜덤 간격)
- [x] 영구 실패 시 Redis DLQ에 저장하여 추적 가능
- [x] 410 (구독 만료) 시 자동 정리 (재시도 안 함)
- [x] DLQ 배치 재처리 (5분 주기)
- [x] 최대 5회 재시도 후 영구 실패 처리
- [x] 클래스 책임 분리 (SRP 준수)
- [x] 구독 정보 TTL 7일 (Redis 메모리 효율화, 재구독 시 갱신)

## 7. 파일 목록
- `src/main/java/com/codehows/daehobe/config/RetryConfig.java`
- `src/main/java/com/codehows/daehobe/notification/webPush/service/PushSubscriptionService.java` (신규)
- `src/main/java/com/codehows/daehobe/notification/webPush/service/WebPushSender.java` (신규, 인터페이스)
- `src/main/java/com/codehows/daehobe/notification/webPush/service/WebPushSenderImpl.java` (신규)
- `src/main/java/com/codehows/daehobe/notification/webPush/service/WebPushService.java` (리팩토링)
- `src/main/java/com/codehows/daehobe/notification/webPush/service/NotificationDlqService.java` (신규)
- `src/main/java/com/codehows/daehobe/notification/exception/PushNotificationException.java` (신규)
- `src/main/java/com/codehows/daehobe/notification/dto/FailedNotificationDto.java`
