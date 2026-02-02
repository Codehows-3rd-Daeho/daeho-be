# H4: RestClient 커넥션 설정

## 1. 변경 사항
- **변경**: WebClient → RestClient 마이그레이션
- **이유**: Spring MVC 환경에서 불필요한 비동기 래핑(Mono) 제거
- **코드 위치**: `RestClientConfig.java` (기존 `WebClientConfig.java` 대체)

## 2. 기존 문제점 (WebClient)

### 이중 블로킹 안티패턴
```java
// 호출자에서 .block()
String rid = sttProvider.requestTranscription(file).block();

// DagloSttProvider 내부에서도 .block()
return Mono.fromCallable(() ->
    webClient.post()
        ...
        .block()  // 내부 블로킹
).get();
```

**문제점:**
1. `Mono<T>` 반환 후 즉시 `.block()` → 무의미한 비동기 래핑
2. Reactor 스택 트레이스로 디버깅 어려움
3. WebFlux 의존성 필요 (Spring MVC 환경에서 불필요)

## 3. 해결 방법: RestClient 전환

### RestClientConfig.java
```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient dagloRestClient(DagloProperties dagloProperties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(dagloProperties.getTimeout()));

        return RestClient.builder()
                .baseUrl(dagloProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + dagloProperties.getToken())
                .requestFactory(requestFactory)
                .build();
    }
}
```

### DagloSttProvider.java (변경 후)
```java
@Override
public String requestTranscription(Resource audioFile) {
    return circuitBreaker.executeSupplier(() -> {
        STTResponseDto response = restClient.post()
                .uri("/stt/v1/async/transcripts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(buildMultipartBody(audioFile))
                .retrieve()
                .body(STTResponseDto.class);

        if (response == null || response.getRid() == null) {
            throw new RuntimeException("rid 발급 실패");
        }
        return response.getRid();
    });
}
```

## 4. JDK HttpClient 커넥션 관리

JDK 11+ HttpClient는 내부적으로 커넥션 풀을 자동 관리합니다:

| 특성 | 설명 |
|------|------|
| 커넥션 재사용 | HTTP/1.1 Keep-Alive, HTTP/2 멀티플렉싱 자동 지원 |
| 풀 관리 | JVM이 내부적으로 최적화된 커넥션 풀 관리 |
| 메모리 효율 | 유휴 연결 자동 정리 |
| 추가 의존성 | 불필요 (JDK 내장) |

### 설정 옵션

| 설정 | 값 | 설명 |
|------|-----|------|
| readTimeout | dagloProperties.timeout | 응답 읽기 타임아웃 |
| connectTimeout | 기본값 (무제한) | 필요시 커스텀 HttpClient로 설정 가능 |

## 5. 마이그레이션 결과

### 변경된 파일
| 파일 | 변경 내용 |
|------|----------|
| `SttProvider.java` | `Mono<T>` → `T` 반환타입 변경 |
| `DagloSttProvider.java` | RestClient 사용, `.block()` 제거 |
| `WebClientConfig.java` | 삭제 → `RestClientConfig.java`로 대체 |
| `STTService.java` | `.block()` 호출 제거 (2곳) |
| `SttJobProcessor.java` | `.block()` 호출 제거 (3곳) |
| `build.gradle` | WebFlux 의존성 제거 |

### 개선 효과
| 항목 | Before | After |
|------|--------|-------|
| 이중 `.block()` 호출 | 5곳 | 0곳 |
| WebFlux 의존성 | 필수 | 제거됨 |
| Reactor 의존성 | 필수 | 제거됨 |
| 스택 트레이스 | Reactor 스택 (복잡) | 동기 스택 (명확) |
| 코드 가독성 | `Mono` 래핑 필요 | 직관적 동기 호출 |

## 6. 검증
- [x] 빌드 성공 (`./gradlew build -x test`)
- [x] 유닛 테스트 통과 (`STTServiceTest`)
- [x] 컴파일 오류 없음
- [x] WebFlux 의존성 제거 확인

## 7. 고급 설정 (필요시)

커넥션 타임아웃 등 세부 설정이 필요한 경우:

```java
@Bean
public RestClient dagloRestClient(DagloProperties dagloProperties) {
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(dagloProperties.getTimeout()));

    return RestClient.builder()
            .baseUrl(dagloProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + dagloProperties.getToken())
            .requestFactory(requestFactory)
            .build();
}
```
