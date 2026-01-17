# STT 서비스 리팩토링 및 트러블슈팅 보고서

- **날짜:** 2026년 1월 17일
- **작성자:** Gemini

## 1. 개요

본 문서는 `com.codehows.daehobe.service.stt` 패키지 및 관련 클래스에 대한 리팩토링 및 트러블슈팅 과정을 요약합니다. 주요 목표는 다음과 같습니다.

1.  **비동기 처리 및 성능 개선:** 전통적인 블로킹(Blocking) 호출 방식을 제거하고, Spring WebFlux 및 Project Reactor를 활용한 완전한 논블로킹(Non-blocking) 비동기 방식으로 전환하여 시스템의 반응성과 처리량을 향상시킵니다.
2.  **코드 구조 개선 및 유지보수성 향상:** 강하게 결합된 코드를 분리하고, 외부 서비스와의 의존성을 낮추며, 설정 및 상수를 중앙에서 관리하여 코드의 유연성과 확장성을 높입니다.
3.  **잠재적 위험 요소 제거:** 컴파일 타임 오류, 스레드 고갈(Thread Starvation) 등 런타임에 발생할 수 있는 잠재적 위험 요소를 사전에 식별하고 해결합니다.

---

## 2. 식별된 문제 및 해결 과정

### 문제 1: 리액티브 컨텍스트에서의 블로킹(Blocking) I/O 호출

- **현상:**
  `DagloService`에서 `WebClient`를 사용하면서 API 호출 결과에 `.block()`을 사용하여 동기적으로 응답을 기다리고 있었습니다. 이는 리액티브 스택의 장점을 무효화하고 요청을 처리하는 이벤트 루프 스레드를 차단하여 시스템 전체의 성능 저하를 유발하는 주요 원인이었습니다.

- **해결:**
  1.  `DagloService`의 모든 메서드가 `Mono`를 반환하도록 시그니처를 변경하고, `.block()` 호출을 제거했습니다.
  2.  `STTService`는 변경된 `DagloService`의 비동기 메서드를 호출하고, 그 결과를 처리하기 위해 `.flatMap()`, `.map()` 등의 리액티브 연산자를 사용하는 체인으로 로직을 수정했습니다.
  3.  `STTController`의 엔드포인트 또한 `Mono`를 반환하도록 하여, 요청부터 응답까지 완전한 비동기 파이프라인이 구성되도록 했습니다.

### 문제 2: 설정 값 하드코딩

- **현상:**
  `SttJobExecutor` 클래스 내에서 파일 서버의 URL이 `"http://localhost:8080"`으로 하드코딩되어 있었습니다. 이로 인해 개발, 테스트, 운영 등 다른 환경에 배포 시 유연하게 대처하기 어려웠습니다.

- **해결:**
  1.  `src/main/resources/application.properties` 파일을 생성하고, `app.base-url=http://localhost:8080` 속성을 추가했습니다.
  2.  `SttJobExecutor`에서 `@Value("${app.base-url}")` 어노테이션을 사용하여 해당 속성 값을 주입받도록 수정했습니다.

### 문제 3: 외부 서비스와의 강한 결합(Tight Coupling)

- **현상:**
  애플리케이션 로직이 `DagloService`라는 특정 구현체에 직접 의존하고 있었습니다. 이는 향후 다른 STT 제공자(e.g., Google, Naver)로 교체하거나 추가할 때 대규모 코드 수정이 불가피한 구조였습니다.

- **해결:**
  1.  **`SttProvider` 인터페이스 도입:** STT 서비스의 공통 기능(변환 요청, 상태 조회, 요약 요청)을 정의하는 `SttProvider` 인터페이스를 생성했습니다.
  2.  **`DagloSttProvider` 구현:** 기존 `DagloService`를 `SttProvider` 인터페이스의 구현체인 `DagloSttProvider`로 리팩토링하고, `@Service("dagloSttProvider")`로 빈(Bean) 이름을 명시했습니다.
  3.  **의존성 역전:** `STTService`가 구체 클래스인 `DagloSttProvider` 대신 `SttProvider` 인터페이스에 의존하도록 수정하여 결합도를 낮췄습니다. (의존성 주입 시 `@Qualifier("dagloSttProvider")` 사용)

### 문제 4: 분산된 Redis 키 관리

- **현상:**
  `"stt:processing"`, `"stt:status:"` 등 Redis에서 사용되는 키(key) 문자열들이 여러 클래스에 `public static final String` 상수로 흩어져 있었습니다. 이는 오타 발생 가능성이 높고, 키 변경 시 모든 사용처를 추적해야 하는 유지보수의 어려움을 야기했습니다.

- **해결:**
  1.  **`SttRedisKeys` 클래스 생성:** 모든 STT 관련 Redis 키를 모아 관리하는 `SttRedisKeys` 클래스를 생성했습니다.
  2.  **중앙 참조:** `SttTaskProcessor`, `STTService`, `SttJobExecutor` 등 관련 클래스들이 모두 `SttRedisKeys`의 상수를 참조하도록 코드를 수정하여 키 관리를 중앙화했습니다.

### 문제 5: 익명 클래스로 인한 타입 불일치 컴파일 오류

- **현상:**
  `STTService`의 `fileToByteArrayResource` 메서드에서 `new ByteArrayResource(...) { ... }` 형태로 익명 클래스를 생성하여 반환했습니다. 이로 인해 컴파일러가 타입을 `Mono<anonymous ByteArrayResource>`로 추론하여, 명시된 반환 타입 `Mono<ByteArrayResource>`와 일치하지 않는다는 컴파일 오류가 발생했습니다.

- **해결:**
  리액티브 체인에 `.map(resource -> (ByteArrayResource) resource)` 연산자를 추가하여, 생성된 익명 클래스 객체를 상위 타입인 `ByteArrayResource`로 명시적으로 캐스팅해주어 타입 불일치 문제를 해결했습니다.

### 문제 6: 논블로킹 컨텍스트에서의 블로킹 호출 (스레드 고갈 위험)

- **현상:**
  JPA 레포지토리 호출과 같은 블로킹(Blocking) 코드를 포함한 서비스 메서드를 컨트롤러에서 `Mono.fromCallable`으로 감쌌지만, 이 작업이 어떤 스레드에서 실행될지 지정하지 않았습니다. 이로 인해 WebFlux의 논블로킹 이벤트 루프 스레드에서 해당 코드가 실행되어 "Possibly blocking call in non-blocking context" 경고가 발생했으며, 실제 운영 시 스레드 고갈로 이어질 위험이 있었습니다.

- **해결:**
  `STTController`의 모든 동기 서비스 호출문에 `.subscribeOn(Schedulers.boundedElastic())` 연산자를 추가했습니다. 이를 통해 블로킹이 발생할 수 있는 작업들을 **블로킹 I/O 전용 스레드 풀(`boundedElastic`)로 위임**하여, 이벤트 루프 스레드가 차단되지 않고 다른 요청을 계속 처리할 수 있도록 보장했습니다.

---

## 3. 결론

위와 같은 일련의 리팩토링 및 트러블슈팅 과정을 통해 `stt` 관련 기능은 이제 Spring WebFlux의 사상에 더 부합하는 안정적이고 효율적인 비동기 코드로 개선되었습니다. 또한, 유연하고 확장 가능한 구조를 갖추게 되어 향후 유지보수 및 기능 추가가 용이해졌습니다.
