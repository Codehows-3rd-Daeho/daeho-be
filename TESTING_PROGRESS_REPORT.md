# TESTING_PROGRESS_REPORT.md

## 1. 지금까지 작업 진행 상황 (Work Done So Far)

### 1.1. 테스트 환경 설정
-   **`build.gradle` 설정**:
    -   테스트 관련 의존성 추가 및 정리: `spring-boot-starter-test`, `h2database`, `spring-kafka-test`, `spring-security-test`, `io.projectreactor:reactor-test`.
    -   JaCoCo 플러그인 추가 및 설정 (보고서 생성 및 특정 클래스 제외).
    -   Spring Boot 버전 `3.4.1`로 업데이트 (사용자에 의해).
-   **`src/test/resources/application.properties`**:
    -   테스트 전용 H2 인메모리 데이터베이스 설정.

### 1.2. 패키지별 테스트 코드 작성

#### `masterData` 패키지 (모든 서비스 및 컨트롤러 테스트 완료)
-   **`DepartmentService`**: 단위 테스트 (`DepartmentServiceTest.java`)
-   **`DepartmentController`**: 웹 계층 통합 테스트 (`DepartmentControllerTest.java`)
-   **`CategoryService`**: 단위 테스트 (`CategoryServiceTest.java`)
-   **`CategoryController`**: 웹 계층 통합 테스트 (`CategoryControllerTest.java`)
-   **`FileSettingService`**: 단위 테스트 (`FileSettingServiceTest.java`)
-   **`FileSettingController`**: 웹 계층 통합 테스트 (`FileSettingControllerTest.java`)
-   **`GroupService`**: 단위 테스트 (`GroupServiceTest.java`)
-   **`GroupController`**: 웹 계층 통합 테스트 (`GroupControllerTest.java`)
-   **`JobPositionService`**: 단위 테스트 (`JobPositionServiceTest.java`)
-   **`JobPositionController`**: 웹 계층 통합 테스트 (`JobPositionControllerTest.java`)
-   **`SetNotificationService`**: 단위 테스트 (`SetNotificationServiceTest.java`)
-   **`SetNotificationController`**: 웹 계층 통합 테스트 (`SetNotificationControllerTest.java`)

#### `member` 패키지
-   **`MemberService`**: 단위 테스트 (`MemberServiceTest.java`)
-   **`MemberController`**: 웹 계층 통합 테스트 (`MemberControllerTest.java`)
-   **`LoginService` 및 `LoginController`**: 사용자 지시에 따라 인증/인가 관련 테스트 제외. (관련 테스트 파일 복원 예정)

#### `issue` 패키지 (모든 서비스 및 컨트롤러 테스트 완료)
-   **`IssueService`**: 단위 테스트 (`IssueServiceTest.java`)
-   **`IssueDepartmentService`**: 단위 테스트 (`IssueDepartmentServiceTest.java`)
-   **`IssueMemberService`**: 단위 테스트 (`IssueMemberServiceTest.java`)
-   **`IssueController`**: 웹 계층 통합 테스트 (`IssueControllerTest.java`)

#### `file` 패키지
-   **`ImageUploadController`**: 웹 계층 통합 테스트 (`ImageUploadControllerTest.java`)
-   **`FileService`**: 테스트 작성 시도했으나 지속적인 환경 문제로 인해 관련 테스트 파일 임시 삭제. (복원 예정)

#### `logging` 패키지 (모든 서비스 및 컨트롤러 테스트 완료)
-   **`LogService`**: 단위 테스트 (`LogServiceTest.java`)
-   **`LogController`**: 웹 계층 통합 테스트 (`LogControllerTest.java`)

#### `meeting` 패키지
-   **`MeetingDepartmentService`**: 단위 테스트 (`MeetingDepartmentServiceTest.java`)
-   **`MeetingMemberService`**: 단위 테스트 (`MeetingMemberServiceTest.java`)
-   **`MeetingController`**: 웹 계층 통합 테스트 (`MeetingControllerTest.java`)
-   **`MeetingService`**: 테스트 작성 시도했으나 지속적인 환경 문제로 인해 관련 테스트 파일 임시 삭제. (복원 예정)

---

## 2. 앞으로 남은 작업 (Remaining Tasks)

### 2.1. 주요 패키지 (`notification`, `stt`, `comment`)
-   각 패키지의 모든 서비스(Service) 클래스에 대한 단위 테스트 작성.
-   각 패키지의 모든 컨트롤러(Controller) 클래스에 대한 웹 계층 통합 테스트 작성.
-   특히 `notification` (Kafka) 및 `stt` (외부 API)와 같이 비동기 또는 외부 연동이 있는 서비스는 특수한 테스트 (예: `@EmbeddedKafka`, `WireMock` 등) 필요 여부 검토.

### 2.2. 복원된 테스트 파일 수정 (사용자 작업)
-   `LoginControllerTest.java`, `LoginServiceTest.java`, `FileServiceTest.java`, `MeetingServiceTest.java` 파일의 문제 수정.

### 2.3. 전반적인 품질 검토
-   테스트 전체 커버리지 90% 이상 달성 목표 유지.
-   정기적으로 JaCoCo 리포트 확인 및 커버리지 미달 영역 보강.

---

## 3. 테스트 코드 세팅 및 컨벤션 (Setting & Conventions)

### 3.1. 테스트 프레임워크 및 도구
-   **JUnit 5**: 테스트 작성의 기본 프레임워크.
-   **Mockito**: 서비스 계층 단위 테스트 및 컨트롤러 계층 통합 테스트 시 의존성 모킹에 활용.
-   **AssertJ**: 가독성 높은 단언(assertion)을 위한 라이브러리.
-   **Spring Boot Test**: `@WebMvcTest` (컨트롤러 테스트), `@SpringBootTest` (전체 컨텍스트 통합 테스트) 등 스프링 테스트 지원 어노테이션 활용.

### 3.2. 의존성 관리
-   **`@Autowired`**: `MockMvc`, `ObjectMapper` 등 스프링이 관리하는 빈 주입.
-   **`@Mock`, `@InjectMocks`**: 서비스 계층 단위 테스트 시 Mockito를 통한 의존성 모킹.
-   **`@MockitoBean`**: 컨트롤러 계층 통합 테스트 시 서비스 레이어를 모킹하여 주입.

### 3.3. 테스트 데이터베이스
-   **H2 Database**: `src/test/resources/application.properties`를 통해 설정된 인메모리 데이터베이스를 `@DataJpaTest` 등 JPA 관련 테스트 시 활용 (단, Repository 테스트는 사용자 요청에 따라 제외됨).

### 3.4. Mockito 사용 요령
-   **스터빙 (Stubbing)**: `when(mock.method()).thenReturn(value)`, `doNothing().when(mock).method()`, `doThrow(exception).when(mock).method()`.
-   **검증 (Verification)**: `verify(mock, times(1)).method(arguments)`, `verify(mock, never()).method(arguments)`.
-   **인자 매처 (Argument Matchers)**: `any()`, `anyLong()`, `anyList()`, `anyString()`, `eq(value)`, `isNull()`. 특정 인자는 `eq()`로 명시하고, 나머지 인자는 `any()` 등으로 처리.
-   **`ArgumentCaptor`**: Mocked 메서드에 전달된 인자를 캡처하여 상세한 검증이 필요할 때 사용 (예: DTO를 엔티티로 변환하여 저장하는 로직).

### 3.5. 테스트 코드 구조 및 명명 규칙
-   **테스트 클래스 명명**: `[클래스명]Test.java` (예: `DepartmentServiceTest`).
-   **테스트 메서드 명명**: `[기능]_Success()`, `[기능]_Failure_[실패원인]()` 등 명확한 시나리오 기반.
-   **`@DisplayName`**: 각 테스트 클래스 및 메서드에 한글로 상세한 설명 추가. `Nested` 클래스를 활용하여 관련 테스트들을 그룹화.
-   **given-when-then 패턴**: 테스트 코드 내에서 준비(given), 실행(when), 검증(then) 단계를 명확히 구분.
-   **주석**: 각 테스트 클래스, 메서드에 Javadoc 스타일의 상세한 주석 추가.

### 3.6. 보안 컨텍스트 (`@WebMvcTest` 활용 시)
-   **`@Import(JwtService.class)`**: 컨트롤러 테스트 시 JWT 관련 빈이 필요할 경우 임포트.
-   **`@WithMockUser`**: 인증된 사용자를 가장하여 보안이 적용된 엔드포인트 테스트 (역할 지정 가능).
-   **`.with(csrf())`**: CSRF(Cross-Site Request Forgery) 보호가 활성화된 엔드포인트 테스트 시 요청에 CSRF 토큰을 포함.

---

## 4. 꼭 유의해야 할 점들 (Important Considerations)

### 4.1. DTO/엔티티 설계와 테스트 용이성
-   **불변 DTO 처리**: 필드를 설정할 수 있는 생성자(`@AllArgsConstructor`) 또는 빌더(`@Builder`) 패턴을 제공하는 것이 테스트 시 객체 생성에 용이합니다.
-   **리플렉션 사용 최소화**: 테스트 코드에서 DTO 필드를 리플렉션으로 설정하는 것은 코드 가독성 및 유지보수를 저해하므로 가능한 피합니다. (예: `LoginDto` 문제 해결 시 `createLoginDto` 헬퍼 메서드 활용).
-   **엔티티 컬렉션 조작**: JPA 엔티티 내 `List`와 같은 컬렉션 필드에 직접 `set*()` 메서드가 없는 경우가 많으므로, 엔티티 빌더를 통해 초기화하거나 엔티티의 비즈니스 로직 메서드(`update()`)를 통해 간접적으로 조작해야 합니다. (예: `Issue` 엔티티의 `issueMembers`, `issueDepartments` 문제).

### 4.2. Mockito Strict Stubbing
-   `org.mockito.exceptions.misusing.PotentialStubbingProblem` 또는 `UnnecessaryStubbingException`: Mockito의 엄격한 스터빙 규칙으로 인해 발생할 수 있습니다. Mock된 메서드는 실제 테스트 흐름에서 반드시 호출되어야 하며, 호출될 것으로 예상되는 모든 메서드를 스터빙해야 합니다. 테스트의 정확한 흐름을 반영하도록 스터빙을 조정합니다.

### 4.3. 테스트 스코프의 적절한 선택
-   **단위 테스트 (Unit Test)**: 서비스 계층 등 순수 비즈니스 로직에 집중하며, 모든 외부 의존성을 Mockito로 모킹하여 격리된 환경에서 빠르게 실행.
-   **웹 계층 통합 테스트 (`@WebMvcTest`)**: 컨트롤러의 요청/응답 처리, 유효성 검사, 예외 처리 등을 검증하며, 서비스 계층은 `@MockBean`으로 모킹하여 웹 계층에 집중.
-   **전체 컨텍스트 통합 테스트 (`@SpringBootTest`)**: 최소한으로 사용하며, 여러 계층 간의 상호 작용 및 전체 애플리케이션 컨텍스트 로딩을 검증할 때만 사용. (현재 `ContextLoadsTest` 및 `DaehoBeApplicationTests`는 제거됨)
