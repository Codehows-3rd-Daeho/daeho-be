# TODO_FAILING_TESTS.md

## 실패한 테스트 목록

다음은 마지막 테스트 실행에서 실패한 테스트 목록입니다. 이 테스트들은 컴파일 또는 런타임 오류로 인해 실패했으며, 주어진 제약 조건 내에서 해결되지 않았습니다.

### 1. `CommentServiceTest.java`
- [ ] `성공: 이슈 댓글 작성 (멘션, 파일 포함)` - `org.mockito.exceptions.misusing.UnnecessaryStubbingException` 또는 `NullPointerException` (이전 실행에서 발생)

### 2. `MentionServiceTest.java`
- [ ] `성공: 멘션 저장 (작성자 제외)` - `org.mockito.exceptions.verification.TooManyActualInvocations` (이전 실행에서 발생)

### 3. `FileServiceTest.java`
- [ ] `성공: 파일 레코드 생성` - `AssertionError` (이전 실행에서 발생)

### 4. `MeetingServiceTest.java`
- [ ] `성공: 회의 생성 (이슈 포함)` - `NullPointerException`
- [ ] `성공: 회의 생성 (이슈 없음)` - `NullPointerException`

### 5. `LoginControllerTest.java`
- [ ] `성공: 로그인 요청` - `AssertionError` (403 Forbidden)
- [ ] `실패: 로그인 요청 (유효성 검증 실패 - 빈 아이디)` - `AssertionError` (403 Forbidden)
- [ ] `실패: 로그인 요청 (유효성 검증 실패 - 짧은 비밀번호)` - `AssertionError` (403 Forbidden)
- [ ] `실패: 로그인 요청 (서비스 예외 - 잘못된 자격 증명)` - `AssertionError` (403 Forbidden)

### 6. `NotificationServiceTest.java`
- [ ] `성공: 알림 목록 조회` - `AssertionError` (이전 실행에서 발생)

### 7. `STTServiceTest.java`
- [ ] `성공: ID로 STT 조회` - `NullPointerException`
- [ ] `성공: 캐시에서 동적 STT 상태 조회 (캐시 히트)` - `NullPointerException`
- [ ] `성공: 회의 ID로 STT 목록 조회` - `NullPointerException`
- [ ] `성공: STT 요약 업데이트` - `org.mockito.exceptions.misusing.MissingMethodInvocationException`
- [ ] `성공: STT 녹음 시작` - `org.mockito.exceptions.misusing.UnnecessaryStubbingException`
- [ ] `성공: STT 파일 업로드 및 번역 요청` - `org.mockito.exceptions.misusing.UnnecessaryStubbingException`
- [ ] `성공: STT 상태 확인` - `org.mockito.exceptions.misusing.UnnecessaryStubbingException`

### 8. `CommentControllerTest.java`
- [ ] `성공: 이슈 댓글 목록 조회` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 이슈 댓글 작성` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 회의 댓글 목록 조회` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 회의 댓글 작성` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 댓글 수정` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 댓글 삭제` - `AssertionError` (500 Internal Server Error)

### 9. `STTControllerTest.java`
- [ ] `성공: 청크 업로드` - `AssertionError` (500 Internal Server Error)
- [ ] `성공: 파일 업로드 및 번역 시작` - `AssertionError` (500 Internal Server Error)
