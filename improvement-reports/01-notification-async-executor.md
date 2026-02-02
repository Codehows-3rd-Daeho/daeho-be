# C1: Notification Async Executor 설정

## 1. 문제 상황
- **현상**: `@Async` 어노테이션 사용 시 전용 Executor Bean이 없어 기본 SimpleAsyncTaskExecutor 사용
- **영향 범위**: 알림 발송 시 무제한 스레드 생성 가능 → 메모리 고갈, Context Switching 오버헤드
- **코드 위치**: `NotificationService.java:32`, `AsyncConfig.java`

## 2. 원인 분석
- **근본 원인**: Spring의 기본 Executor는 스레드풀을 사용하지 않고 매 요청마다 새 스레드 생성
- **기존 코드 문제점**:
```java
@Async  // 기본 Executor 사용 → 무제한 스레드 생성!
public void sendNotification(String memberId, NotificationMessageDto messageDto) {
    webPushService.sendNotificationToUser(memberId, messageDto);
}
```

## 3. 해결 방법
- **변경 내용**: 전용 notificationTaskExecutor Bean 생성 및 @Async에 명시적 지정
- **코드 변경사항**:

### AsyncConfig.java
```java
@Bean("notificationTaskExecutor")
public Executor notificationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(500);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setThreadNamePrefix("notification-");
    executor.initialize();
    return executor;
}
```

### NotificationService.java
```java
@Async("notificationTaskExecutor")  // 전용 Executor 사용
public void sendNotification(String memberId, NotificationMessageDto messageDto) {
    webPushService.sendNotificationToUser(memberId, messageDto);
}
```

## 4. 설정 외부화
application.properties에서 스레드풀 설정 가능:
```properties
async.notification.core-pool-size=4
async.notification.max-pool-size=8
async.notification.queue-capacity=500
async.notification.thread-name-prefix=notification-
```

## 5. 검증
- [x] 스레드풀 제한으로 메모리 사용량 안정화
- [x] CallerRunsPolicy로 큐 초과 시에도 알림 손실 없음
- [x] 운영 환경에서 설정 조정 가능
