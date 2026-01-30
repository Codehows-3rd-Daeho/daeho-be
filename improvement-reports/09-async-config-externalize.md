# M2: Async 설정 외부화

## 1. 문제 상황
- **현상**: Async 스레드풀 설정이 코드에 하드코딩
- **영향 범위**: 운영 환경에서 튜닝 불가, 재배포 필요
- **코드 위치**: `AsyncConfig.java`

## 2. 원인 분석
- **근본 원인**: 설정값 직접 코딩
- **기존 코드 문제점**:
```java
@Bean("sttTaskExecutor")
public Executor sttTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);  // 하드코딩!
    executor.setMaxPoolSize(4);   // 하드코딩!
    executor.setQueueCapacity(100);  // 하드코딩!
    // ...
}
```

## 3. 해결 방법
- **변경 내용**: ConfigurationProperties로 설정 외부화

### AsyncProperties.java
```java
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "async")
public class AsyncProperties {

    private ExecutorProperties stt = new ExecutorProperties(2, 4, 100, "stt-task-");
    private ExecutorProperties notification = new ExecutorProperties(4, 8, 500, "notification-");

    @Getter
    @Setter
    public static class ExecutorProperties {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private String threadNamePrefix;

        public ExecutorProperties() {}

        public ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity, String threadNamePrefix) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
```

### AsyncConfig.java
```java
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final AsyncProperties asyncProperties;

    @Bean("sttTaskExecutor")
    public Executor sttTaskExecutor() {
        AsyncProperties.ExecutorProperties props = asyncProperties.getStt();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.initialize();
        return executor;
    }

    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        AsyncProperties.ExecutorProperties props = asyncProperties.getNotification();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.initialize();
        return executor;
    }
}
```

### application.properties
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
```

## 4. 검증
- [x] 재배포 없이 설정 변경 가능 (환경변수, ConfigMap 등)
- [x] 환경별 다른 설정 적용 가능 (dev/staging/prod)
- [x] 기본값 제공으로 설정 누락 시에도 동작
