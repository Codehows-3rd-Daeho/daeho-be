# C3: Daglo API Circuit Breaker 적용

## 1. 문제 상황
- **현상**: Daglo API 장애 시 모든 요청이 실패하며 대기
- **영향 범위**: 장애가 폴링 스케줄러로 전파, 리소스 점유 지속
- **코드 위치**: `DagloSttProvider.java` 전체

## 2. 원인 분석
- **근본 원인**: 외부 API 장애에 대한 방어 로직 없음
- **문제점**: 연쇄 장애 발생 가능

## 3. 해결 방법
- **변경 내용**: Resilience4j Circuit Breaker 적용

### 의존성 추가 (build.gradle)
```groovy
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
```

### Resilience4jConfig.java
```java
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)           // 50% 실패 시 Open
                .waitDurationInOpenState(Duration.ofSeconds(30))  // 30초 대기
                .permittedNumberOfCallsInHalfOpenState(3)  // Half-Open 시 3회 테스트
                .slidingWindowSize(10)              // 최근 10회 요청 기준
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker dagloApiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("dagloApi");
    }
}
```

### DagloSttProvider.java
```java
@Override
public Mono<SttTranscriptionResult> checkTranscriptionStatus(String jobId) {
    try {
        return Mono.fromCallable(() -> circuitBreaker.decorateSupplier(() ->
                webClient.get()
                        .uri("/stt/v1/async/transcripts/{rid}", jobId)
                        .retrieve()
                        .bodyToMono(STTResponseDto.class)
                        .map(SttTranscriptionResult::from)
                        .block()
        ).get()).onErrorResume(CallNotPermittedException.class, e -> {
            log.warn("Circuit breaker is open for checkTranscriptionStatus.");
            return Mono.just(SttTranscriptionResult.stillProcessing());  // Fallback
        });
    } catch (Exception e) {
        return Mono.just(SttTranscriptionResult.stillProcessing());
    }
}
```

### SttTranscriptionResult.java (Fallback 메서드 추가)
```java
public static SttTranscriptionResult stillProcessing() {
    return SttTranscriptionResult.builder()
            .completed(false)
            .content("")
            .progress(0)
            .build();
}
```

## 4. 동작 방식
1. **Closed**: 정상 상태, 모든 요청 통과
2. **Open**: 10회 중 5회 이상 실패 → 30초간 요청 차단, Fallback 반환
3. **Half-Open**: 30초 후 3회 테스트 요청 허용, 성공 시 Closed로 복귀

## 5. 검증
- [x] Daglo API 장애 시 빠른 Fallback 응답
- [x] 장애 전파 차단
- [x] 자동 복구 (30초 후)
