# 테스트 명세서 (Test Specification)

> **총 260개 테스트** | 단위 테스트 228개 + 통합 테스트 32개
> 모든 테스트에 `PerformanceLoggingExtension` 적용 (실행 시간 / 메모리 사용량 측정)

---

## 목차

1. [단위 테스트 - 서비스](#1-단위-테스트---서비스)
2. [단위 테스트 - 컨트롤러](#2-단위-테스트---컨트롤러)
3. [통합 테스트](#3-통합-테스트)

---

## 1. 단위 테스트 - 서비스

Mockito 기반 단위 테스트. 외부 의존성을 모두 Mock 처리하여 순수 비즈니스 로직만 검증합니다.

---

### 1-1. CommentService (8개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 이슈 ID로 댓글 목록 조회 | `getCommentsByIssueId_Success` | 페이지네이션 조회, 댓글 내용 일치 |
| 2 | 성공: 이슈 댓글 작성 (멘션, 파일 포함) | `createIssueComment_FullScenario` | 댓글 저장, 멘션 저장, 알림 발송, 파일 업로드 호출 |
| 3 | 성공: 회의 ID로 댓글 목록 조회 | `getCommentsByMeetingId_Success` | 페이지네이션 조회, 댓글 내용 일치 |
| 4 | 성공: 회의 댓글 작성 (멘션, 파일 포함) | `createMeetingComment_FullScenario` | 댓글 저장, 멘션 저장, 알림 발송, 파일 업로드 호출 |
| 5 | 성공: 댓글 수정 (멘션, 파일 변경 포함) | `updateComment_FullScenario` | 내용 업데이트, 멘션 갱신, 새 멘션 알림, 파일 수정 |
| 6 | 성공: 댓글 삭제 | `deleteComment_Success` | 논리 삭제(delete) 호출 |
| 7 | 성공: ID로 댓글 조회 | `getCommentById_Success` | 댓글 반환, 내용 일치 |
| 8 | 실패: ID로 댓글 조회 (없음) | `getCommentById_NotFound` | `EntityNotFoundException` 발생 |

### 1-2. MentionService (7개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 멘션 저장 (작성자 제외) | `saveMentions_Success_ExcludingWriter` | 작성자 제외 후 2명만 save |
| 2 | 성공: 멘션 저장 (중복 ID 및 작성자 제외) | `saveMentions_Success_DuplicateAndWriterExcluded` | 중복 제거 + 작성자 제외 후 1명만 save |
| 3 | 성공: 멘션 저장 (null/empty) | `saveMentions_NullOrEmptyIds_NoSave` | memberService 미호출, save 미호출 |
| 4 | 성공: 댓글로 멘션 목록 조회 | `getMentionsByComment_Success` | 멘션 2건, memberId/name 일치 |
| 5 | 성공: 멘션 업데이트 (새 멘션 포함) | `updateMentions_Success_WithNewMentions` | 기존 삭제 후 새 멘션 저장 |
| 6 | 성공: 멘션 업데이트 (null/empty) | `updateMentions_NullOrEmptyIds_DeletesExisting` | 기존 멘션 삭제만 수행 |
| 7 | 성공: 멘션된 회원 ID 목록 조회 | `getMentionedMemberIds_Success` | ID 목록 [2, 3] 반환 |

### 1-3. FileService (4개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 파일 레코드 생성 | `createFile_Success` | 파일명 일치, save 호출 |
| 2 | 성공: 이슈 파일 목록 조회 | `getIssueFiles_Success` | FileDto 1건, 파일명 일치 |
| 3 | 성공: 멤버 프로필 파일 조회 | `findFirstByTargetIdAndTargetType_Success` | 파일 반환, 파일명 일치 |
| 4 | 성공: 멤버 프로필 파일 조회 (없음) | `findFirstByTargetIdAndTargetType_NotFound` | null 반환 |

### 1-4. IssueService (13개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 이슈 생성 (전체 시나리오) | `createIssue_Success_FullScenario` | 이슈 저장, 파일 업로드, 부서/참여자 저장, 알림 발송 |
| 2 | 성공: 이슈 생성 (최소 시나리오) | `createIssue_Success_MinimalScenario` | 파일/부서/참여자/알림 없이 이슈만 저장 |
| 3 | 성공: 공개 이슈 상세 조회 | `getIssueDtl_PublicIssue_Success` | 이슈 반환, 읽음 상태 업데이트 |
| 4 | 성공: 비밀 이슈 상세 조회 (참여자) | `getIssueDtl_PrivateIssue_Participant_Success` | 참여자일 경우 정상 조회 |
| 5 | 실패: 비밀 이슈 상세 조회 (비참여자) | `getIssueDtl_PrivateIssue_NonParticipant_Failure` | `RuntimeException` 발생 |
| 6 | 실패: 이슈 상세 조회 (없음) | `getIssueDtl_IssueNotFound_Failure` | `RuntimeException` 발생 |
| 7 | 성공: 읽음 상태 업데이트 (false→true) | `updateReadStatus_WhenFalse_Success` | isRead true로 변경 |
| 8 | 성공: 읽음 상태 업데이트 (이미 true) | `updateReadStatus_WhenTrue_NoChange` | 변경 없음 |
| 9 | 성공: 이슈 업데이트 (파일 변경, 상태 미변경) | `updateIssue_Success_WithFileChanges_NoStatusChange` | 파일 수정, 알림 미발송 |
| 10 | 성공: 이슈 업데이트 (상태 변경, 알림 발송) | `updateIssue_Success_WithStatusChange_SendsNotification` | 상태 변경, 알림 발송 |
| 11 | 실패: 이슈 업데이트 (없음) | `updateIssue_IssueNotFound_Failure` | `EntityNotFoundException` 발생 |
| 12 | 성공: 이슈 삭제 | `deleteIssue_Success` | isDel true |
| 13 | 실패: 이슈 삭제 (없음) | `deleteIssue_NotFound_Failure` | `EntityNotFoundException` 발생 |

### 1-5. IssueMemberService (9개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 이슈 참여자 저장 | `saveIssueMember_Success` | save 호출 |
| 2 | 실패: 이슈 참여자 저장 (이슈 없음) | `saveIssueMember_IssueNotFound` | 예외 발생 |
| 3 | 성공: 참여자 목록 조회 | `getMembers_Success` | 목록 반환 |
| 4 | 성공: 주관자 조회 | `getHost_Success` | 주관자 반환 |
| 5 | 성공: 참여자 엔티티 조회 | `getMember_Success` | 엔티티 반환 |
| 6 | 성공: 이슈 참여자 삭제 | `deleteIssueMember_Success` | 삭제 호출 |
| 7 | 성공: 멤버 ID로 참여 이슈 페이지 조회 | `findByMemberId_Success` | 페이지 반환 |
| 8 | 성공: 이슈+멤버 ID로 참여자 조회 | `findByIssueIdAndMemberId_Success` | 참여자 반환 |
| 9 | 실패: 이슈+멤버 ID로 참여자 조회 (없음) | `findByIssueIdAndMemberId_NotFound` | null 반환 |

### 1-6. IssueDepartmentService (5개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 이슈 부서 저장 | `saveDepartment_Success` | save 호출 |
| 2 | 실패: 이슈 부서 저장 (이슈 없음) | `saveDepartment_IssueNotFound` | 예외 발생 |
| 3 | 성공: 이슈로 부서 이름 조회 | `getDepartmentName_Success` | 이름 목록 반환 |
| 4 | 성공: 이슈로 부서 엔티티 조회 | `getDepartMent_Success` | 엔티티 목록 반환 |
| 5 | 성공: 이슈 부서 삭제 | `deleteIssueDepartment_Success` | 삭제 호출 |

### 1-7. MeetingService (5개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 회의 생성 (이슈 포함) | `createMeeting_WithIssue_Success` | 회의 저장, 이슈 연결, 참여자 저장 |
| 2 | 성공: 회의 생성 (이슈 없음) | `createMeeting_NoIssue_Success` | 이슈 null, issueService 미호출 |
| 3 | 성공: 회의 삭제 | `deleteMeeting_Success` | isDel true |
| 4 | 실패: 회의 삭제 (없음) | `deleteMeeting_NotFound` | `EntityNotFoundException` 발생 |
| 5 | 성공: ID로 회의 조회 | `getMeetingById_Success` | 회의 반환 |

### 1-8. MemberService (8개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 회원 생성 | `createMember_Success` | 비밀번호 암호화, save 호출 |
| 2 | 성공: 회원 목록 조회 | `findAll_Success` | 페이지 반환 |
| 3 | 성공: 회원 상세 조회 | `getMemberDtl_Success` | DTO 반환, 필드 일치 |
| 4 | 실패: 회원 상세 조회 (없음) | `getMemberDtl_NotFound` | 예외 발생 |
| 5 | 성공: 임시 비밀번호 생성 | `generatePwd_Success` | 8자리 비밀번호, 암호화 저장 |
| 6 | 성공: 회원 정보 수정 | `updateMember_Success` | 정보 업데이트 호출 |
| 7 | 성공: ID로 회원 조회 | `getMemberById_Success` | 회원 반환 |
| 8 | 실패: ID로 회원 조회 (없음) | `getMemberById_NotFound` | `EntityNotFoundException` 발생 |

### 1-9. LoginService (2개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 로그인 | `login_Success` | JWT 토큰 발급, 회원 정보 반환 |
| 2 | 실패: 로그인 (잘못된 자격 증명) | `login_Failure_BadCredentials` | `BadCredentialsException` 발생 |

### 1-10. NotificationService (5개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 알림 저장 | `saveNotification_Success` | save 호출 |
| 2 | 성공: 알림 목록 조회 | `getMyNotifications_Success` | 페이지 반환, 보낸사람 이름 일치 |
| 3 | 성공: 알림 읽음 처리 | `readNotification_Success` | setIsRead 호출 |
| 4 | 실패: 알림 읽음 처리 (없음) | `readNotification_NotFound` | `EntityNotFoundException` 발생 |
| 5 | 성공: 읽지 않은 알림 수 조회 | `getUnreadCount_Success` | 카운트 5 반환 |

### 1-11. STTService (7개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: ID로 STT 조회 | `getSTTById_Success` | STTDto 반환, 파일 정보 포함 |
| 2 | 성공: 동적 STT 상태 조회 (캐시 히트) | `getDynamicSttStatus_CacheHit_Success` | 캐시에서 반환, DB 미조회 |
| 3 | 성공: 동적 STT 상태 조회 (캐시 미스) | `getDynamicSttStatus_CacheMiss_Success` | 캐시 미스 시 DB 조회 |
| 4 | 성공: 회의 ID로 STT 목록 조회 | `getSTTsByMeetingId_Success` | 목록 1건, 파일 정보 포함 |
| 5 | 성공: STT 요약 업데이트 | `updateSummary_Success` | updateSummary 호출 |
| 6 | 성공: STT 녹음 시작 | `startRecording_Success` | STT 생성, 캐시 저장, WebSocket 발행 |
| 7 | 성공: STT 파일 업로드 및 번역 요청 | `uploadAndTranslate_Success` | 파일 업로드, 캐시 저장, Kafka 전송 |

### 1-12. LogService (4개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 이슈 로그 조회 | `getIssueLogs_Success` | 이슈 로그 목록 반환 |
| 2 | 성공: 회의 로그 조회 | `getMeetingLogs_Success` | 회의 로그 목록 반환 |
| 3 | 성공: 모든 로그 조회 (타입 지정) | `getAllLogs_WithTargetType` | 타입 필터링 결과 |
| 4 | 성공: 모든 로그 조회 (타입 미지정) | `getAllLogs_WithoutTargetType` | 전체 로그 결과 |

### 1-13. CategoryService (10개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: ID로 카테고리 조회 | `getCategoryById_Success` | 카테고리 반환 |
| 2 | 실패: ID로 카테고리 조회 (없음) | `getCategoryById_NotFound` | `EntityNotFoundException` |
| 3 | 성공: 모든 카테고리 조회 | `findAll_Success` | 목록 반환 |
| 4 | 성공: 카테고리 없음 시 조회 | `findAll_EmptyList` | 빈 목록 |
| 5 | 성공: 카테고리 생성 | `createCategory_Success` | save 호출 |
| 6 | 실패: 중복 이름 생성 | `createCategory_DuplicateName_ThrowsException` | `IllegalArgumentException` |
| 7 | 성공: 카테고리 삭제 | `deleteCategory_Success` | delete 호출 |
| 8 | 실패: 삭제 (없음) | `deleteCategory_NotFound_ThrowsException` | `EntityNotFoundException` |
| 9 | 성공: 카테고리 이름 업데이트 | `updateCategory_Success` | 이름 변경 |
| 10 | 실패: 업데이트 (없음) | `updateCategory_NotFound_ThrowsException` | `EntityNotFoundException` |

### 1-14. DepartmentService (11개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: ID로 부서 조회 | `getDepartmentById_Success` | 부서 반환 |
| 2 | 실패: ID로 부서 조회 (없음) | `getDepartmentById_NotFound` | `EntityNotFoundException` |
| 3 | 성공: ID가 null일 때 조회 | `getDepartmentById_NullId` | null 반환 |
| 4 | 성공: 모든 부서 조회 | `findAll_Success` | 목록 반환 |
| 5 | 성공: 부서 없음 시 조회 | `findAll_EmptyList` | 빈 목록 |
| 6 | 성공: 부서 생성 | `createDpt_Success` | save 호출 |
| 7 | 실패: 중복 이름 생성 | `createDpt_DuplicateName_ThrowsException` | `IllegalArgumentException` |
| 8 | 성공: 부서 삭제 | `deleteDpt_Success` | delete 호출 |
| 9 | 실패: 삭제 (없음) | `deleteDpt_NotFound_ThrowsException` | `EntityNotFoundException` |
| 10 | 성공: 부서 이름 업데이트 | `updateDpt_Success` | 이름 변경 |
| 11 | 실패: 업데이트 (없음) | `updateDpt_NotFound_ThrowsException` | `EntityNotFoundException` |

### 1-15. JobPositionService (7개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 모든 직급 조회 | `findAll_Success` | 목록 반환 |
| 2 | 성공: 직급 생성 | `createPos_Success` | save 호출 |
| 3 | 실패: 중복 직급 생성 | `createPos_Duplicate` | `IllegalArgumentException` |
| 4 | 성공: 직급 삭제 | `deletePos_Success` | delete 호출 |
| 5 | 실패: 삭제 (없음) | `deletePos_NotFound` | `EntityNotFoundException` |
| 6 | 성공: 직급 업데이트 | `updatePos_Success` | 이름 변경 |
| 7 | 실패: 업데이트 (없음) | `updatePos_NotFound` | `EntityNotFoundException` |

### 1-16. FileSettingService (10개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 파일 크기 설정 조회 (있음) | `getFileSize_Existing` | DTO 반환 |
| 2 | 성공: 파일 크기 설정 조회 (없음) | `getFileSize_NotExisting` | null 반환 |
| 3 | 성공: 파일 크기 설정 업데이트 | `saveFileSize_Update` | 기존 설정 업데이트 |
| 4 | 성공: 파일 크기 설정 생성 | `saveFileSize_Create` | 새 설정 생성 |
| 5 | 성공: 모든 확장자 조회 | `getExtensions_Success` | 목록 반환 |
| 6 | 성공: 확장자 없음 시 조회 | `getExtensions_EmptyList` | 빈 목록 |
| 7 | 성공: 확장자 저장 | `saveExtension_Success` | save 호출 |
| 8 | 실패: 중복 확장자 저장 | `saveExtension_Duplicate` | 예외 발생 |
| 9 | 성공: 확장자 삭제 | `deleteExtension_Success` | delete 호출 |
| 10 | 실패: 삭제 (없음) | `deleteExtension_NotFound` | 예외 발생 |

### 1-17. GroupService (7개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 그룹 생성 | `createGroup_Success` | save 호출 |
| 2 | 성공: 그룹 목록 조회 | `getGroupList_Success` | 목록 반환 |
| 3 | 성공: 그룹 없음 시 조회 | `getGroupList_Empty` | 빈 목록 |
| 4 | 성공: 그룹 업데이트 | `updateGroup_Success` | 이름 변경 |
| 5 | 실패: 업데이트 (없음) | `updateGroup_NotFound` | 예외 발생 |
| 6 | 성공: 그룹 삭제 | `deleteGroup_Success` | delete 호출 |
| 7 | 실패: 삭제 (없음) | `deleteGroup_NotFound` | 예외 발생 |

### 1-18. SetNotificationService (3개)

| # | 시나리오 | 메서드 | 검증 내용 |
|---|---------|--------|-----------|
| 1 | 성공: 알림 설정 저장 | `saveSetting_Success` | save 호출 |
| 2 | 성공: 알림 설정 조회 (있음) | `getSetting_Existing` | 설정 반환 |
| 3 | 성공: 알림 설정 조회 (없음, 기본값 생성) | `getSetting_NotExisting_CreatesDefault` | 기본값 생성 후 반환 |

---

## 2. 단위 테스트 - 컨트롤러

`@WebMvcTest` 기반 슬라이스 테스트. Spring MVC 계층만 로드하여 HTTP 요청/응답, 인증, 유효성 검증을 테스트합니다.

---

### 2-1. CommentController (6개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 이슈 댓글 목록 조회 | `GET /issue/{id}/comments` | 200 OK, 댓글 내용 일치 |
| 2 | 성공: 이슈 댓글 작성 | `POST /issue/{id}/comment` | 200 OK, id/content 반환 |
| 3 | 성공: 회의 댓글 목록 조회 | `GET /meeting/{id}/comments` | 200 OK, 댓글 내용 일치 |
| 4 | 성공: 회의 댓글 작성 | `POST /meeting/{id}/comment` | 200 OK, id/content 반환 |
| 5 | 성공: 댓글 수정 | `PUT /comment/{id}` | 200 OK |
| 6 | 성공: 댓글 삭제 | `DELETE /comment/{id}` | 200 OK |

### 2-2. ImageUploadController (4개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 이미지 업로드 | `POST /image/upload` | 200 OK, URL 반환 |
| 2 | 실패: 빈 파일 업로드 | `POST /image/upload` | 400 Bad Request |
| 3 | 실패: 이미지 파일이 아님 | `POST /image/upload` | 400 Bad Request |
| 4 | 실패: 서비스 예외 발생 | `POST /image/upload` | 500 Internal Server Error |

### 2-3. IssueController (4개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 이슈 생성 | `POST /issue/create` | 200 OK, 이슈 ID 반환 |
| 2 | 성공: 이슈 상세 조회 | `GET /issue/{id}` | 200 OK |
| 3 | 성공: 이슈 수정 | `PUT /issue/{id}` | 200 OK |
| 4 | 성공: 이슈 삭제 | `DELETE /issue/{id}` | 200 OK |

### 2-4. MeetingController (4개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 회의 생성 | `POST /meeting/create` | 200 OK |
| 2 | 성공: 회의 상세 조회 | `GET /meeting/{id}` | 200 OK |
| 3 | 성공: 회의 목록 조회 | `GET /meeting/list` | 200 OK |
| 4 | 성공: 회의 삭제 | `DELETE /meeting/{id}` | 200 OK |

### 2-5. LoginController (4개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 로그인 요청 | `POST /login` | 200 OK, JWT 토큰 반환 |
| 2 | 실패: 빈 아이디 | `POST /login` | 400 Bad Request, "아이디를 입력해주세요." |
| 3 | 실패: 짧은 비밀번호 | `POST /login` | 400 Bad Request, "비밀번호는 최소 8자 이상" |
| 4 | 실패: 잘못된 자격 증명 | `POST /login` | `ServletException` (BadCredentials) |

### 2-6. MemberController (14개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 회원 목록 조회 (키워드 없음) | `GET /admin/member` | 200 OK, 목록 반환 |
| 2 | 성공: 회원 목록 조회 (키워드 포함) | `GET /admin/member?keyword=` | 200 OK |
| 3 | 실패: 회원 목록 조회 오류 | `GET /admin/member` | 500 |
| 4 | 성공: 회원 상세 조회 | `GET /admin/member/{id}` | 200 OK |
| 5 | 실패: 회원 상세 조회 오류 | `GET /admin/member/{id}` | 500 |
| 6 | 성공: 임시 비밀번호 생성 | `POST /admin/member/{id}/generatePwd` | 200 OK |
| 7 | 실패: 임시 비밀번호 오류 | `POST /admin/member/{id}/generatePwd` | 500 |
| 8 | 성공: 회원 정보 수정 (파일 없음) | `PUT /admin/member/{id}` | 200 OK |
| 9 | 성공: 회원 정보 수정 (파일 포함) | `PUT /admin/member/{id}` | 200 OK |
| 10 | 실패: 회원 정보 수정 오류 | `PUT /admin/member/{id}` | 500 |
| 11 | 성공: 마이페이지 조회 | `GET /mypage/{id}` | 200 OK |
| 12 | 실패: 마이페이지 조회 오류 | `GET /mypage/{id}` | 500 |
| 13 | 성공: 비밀번호 재설정 | `PATCH /mypage/password` | 200 OK |
| 14 | 실패: 비밀번호 재설정 오류 | `PATCH /mypage/password` | 500 |

### 2-7. NotificationController (6개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 내 알림 조회 | `GET /notification/my` | 200 OK |
| 2 | 실패: 내 알림 조회 오류 | `GET /notification/my` | 500 |
| 3 | 성공: 알림 읽음 처리 | `PATCH /notification/{id}` | 200 OK |
| 4 | 실패: 알림 읽음 처리 오류 | `PATCH /notification/{id}` | 500 |
| 5 | 성공: 읽지 않은 알림 수 조회 | `GET /notification/unread` | 200 OK |
| 6 | 실패: 읽지 않은 알림 수 오류 | `GET /notification/unread` | 500 |

### 2-8. STTController (8개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: STT 목록 조회 | `GET /stt/meeting/{id}` | 200 OK, STT 목록 |
| 2 | 성공: STT 상태 조회 | `GET /stt/status/{id}` | 200 OK, 상태 반환 |
| 3 | 성공: 녹음 시작 | `POST /stt/recording/start` | 200 OK, RECORDING 상태 |
| 4 | 성공: 청크 업로드 | `POST /stt/{sttId}/chunk` | 200 OK |
| 5 | 성공: STT 요약 업데이트 | `PATCH /stt/{id}/summary` | 200 OK |
| 6 | 성공: STT 삭제 | `DELETE /stt/{id}` | 200 OK |
| 7 | 성공: 파일 업로드 및 번역 시작 | `POST /stt/upload/{id}` | 200 OK, PROCESSING 상태 |
| 8 | 성공: 녹음 완료 및 번역 시작 | `POST /stt/{sttId}/recording/finish` | 200 OK, PROCESSING 상태 |

### 2-9. LogController (3개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 성공: 이슈 로그 조회 | `GET /log/issue/{id}` | 200 OK |
| 2 | 성공: 회의 로그 조회 | `GET /log/meeting/{id}` | 200 OK |
| 3 | 성공: 전체 로그 조회 | `GET /log/all` | 200 OK |

### 2-10. 기준 데이터 컨트롤러 (49개)

**CategoryController** (10개), **DepartmentController** (14개), **JobPositionController** (7개), **FileSettingController** (8개), **GroupController** (8개), **SetNotificationController** (3개)

각 컨트롤러의 CRUD 성공/실패 케이스, 중복 검증, 500 에러 처리를 포함합니다.

---

## 3. 통합 테스트

`@SpringBootTest` + `@AutoConfigureMockMvc` 기반. 실제 Spring 컨텍스트에서 H2 인메모리 DB를 사용하며, Redis/Kafka는 Mock 처리합니다.

---

### 3-1. 회원 관리 API (7개)

| # | 순서 | 시나리오 | 엔드포인트 | 검증 내용 |
|---|------|---------|-----------|-----------|
| 1 | 1 | 회원가입 성공 | `POST /signup` | 200 OK, DB에 회원 존재 확인 |
| 2 | 2 | 아이디 중복 확인 | `GET /signup/check_loginId` | 200 OK, `exists: false` |
| 3 | 3 | 로그인 성공 - JWT 발급 | `POST /login` | 200 OK, token/memberId/name 존재 |
| 4 | 4 | 로그인 실패 - 유효성 검증 | `POST /login` | 400 Bad Request |
| 5 | 5 | 관리자 - 회원 목록 조회 | `GET /admin/member` | 200 OK |
| 6 | 6 | 인증 없이 접근 시 401 | `GET /admin/member` | 401 Unauthorized |
| 7 | 7 | 일반 유저로 관리자 API 접근 시 403 | `GET /admin/member` | 403 Forbidden |

### 3-2. 이슈 관리 API (6개)

| # | 순서 | 시나리오 | 엔드포인트 | 검증 내용 |
|---|------|---------|-----------|-----------|
| 1 | 1 | 이슈 생성 | `POST /issue/create` | 200 OK, 이슈 ID 반환 |
| 2 | 2 | 이슈 상세 조회 | `GET /issue/{id}` | 200 OK, 제목 일치 |
| 3 | 3 | 이슈 칸반 데이터 조회 | `GET /issue/kanban` | 200 OK |
| 4 | 4 | 이슈 리스트 조회 (페이징) | `GET /issue/list` | 200 OK |
| 5 | 5 | 이슈 삭제 (논리 삭제) | `DELETE /issue/{id}` | 200 OK |
| 6 | 6 | 인증 없이 접근 시 401 | `GET /issue/list` | 401 Unauthorized |

### 3-3. 회의 관리 API (6개)

| # | 순서 | 시나리오 | 엔드포인트 | 검증 내용 |
|---|------|---------|-----------|-----------|
| 1 | 1 | 회의 생성 | `POST /meeting/create` | 200 OK, 회의 ID 반환 |
| 2 | 2 | 회의 상세 조회 | `GET /meeting/{id}` | 200 OK, 제목 일치 |
| 3 | 3 | 회의 리스트 조회 (페이징) | `GET /meeting/list` | 200 OK |
| 4 | 4 | 회의 캘린더 조회 | `GET /meeting/scheduler` | 200 OK |
| 5 | 5 | 회의 삭제 (논리 삭제) | `DELETE /meeting/{id}` | 200 OK |
| 6 | 6 | 인증 없이 접근 시 401 | `GET /meeting/list` | 401 Unauthorized |

### 3-4. 기준 데이터 관리 API (9개)

| # | 시나리오 | 엔드포인트 | 검증 내용 |
|---|---------|-----------|-----------|
| 1 | 카테고리 목록 조회 | `GET /masterData/category` | 200 OK, 배열 반환, name 존재 |
| 2 | 카테고리 생성 (관리자) | `POST /admin/category` | 200 OK, name 일치 |
| 3 | 카테고리 중복 생성 시 400 | `POST /admin/category` | 400 Bad Request |
| 4 | 부서 목록 조회 | `GET /masterData/department` | 200 OK, 배열 반환 |
| 5 | 부서 생성 (관리자) | `POST /admin/department` | 200 OK, name 일치 |
| 6 | 부서 중복 생성 시 400 | `POST /admin/department` | 400 Bad Request |
| 7 | 직급 목록 조회 | `GET /masterData/jobPosition` | 200 OK, 배열 반환 |
| 8 | 직급 생성 (관리자) | `POST /admin/jobPosition` | 200 OK, name 일치 |
| 9 | 일반 유저로 관리자 API 접근 시 403 | `POST /admin/category` | 403 Forbidden |

### 3-5. 댓글 관리 API (4개)

| # | 순서 | 시나리오 | 엔드포인트 | 검증 내용 |
|---|------|---------|-----------|-----------|
| 1 | 1 | 이슈 댓글 작성 | `POST /issue/{id}/comment` | DB 댓글 수 증가 확인 |
| 2 | 2 | 이슈 댓글 목록 조회 | `GET /issue/{id}/comments` | 200 OK |
| 3 | 3 | 댓글 삭제 | `DELETE /comment/{id}` | 200 OK |
| 4 | 4 | 인증 없이 접근 시 401 | `GET /issue/{id}/comments` | 401 Unauthorized |

---

## 테스트 인프라

### 성능 측정

모든 테스트에 `PerformanceLoggingExtension`이 적용되어 있습니다.

```
[Performance] MeetingServiceTest.회의 생성 (이슈 포함) - Time: 45 ms | Memory: 2.31 MB
```

### 테스트 환경

| 항목 | 설정 |
|------|------|
| DB | H2 In-Memory (MySQL 호환 모드) |
| DDL | `create-drop` |
| Kafka | `auto-startup=false` |
| Redis | Mock 처리 (`IntegrationTestConfig`) |
| WebPush | Mock 처리 (`IntegrationTestConfig`) |
| 인증 | JWT (테스트용 키) |
| 커버리지 | JaCoCo |

### 실행 명령

```bash
# 전체 테스트
./gradlew test

# 커버리지 리포트
./gradlew test jacocoTestReport

# 특정 모듈만
./gradlew test --tests "com.codehows.daehobe.meeting.*"

# 통합 테스트만
./gradlew test --tests "com.codehows.daehobe.integration.*"
```
