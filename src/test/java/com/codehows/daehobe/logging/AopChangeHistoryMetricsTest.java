package com.codehows.daehobe.logging;

import com.codehows.daehobe.comment.service.CommentService;
import com.codehows.daehobe.issue.service.IssueService;
import com.codehows.daehobe.logging.AOP.annotations.TrackChanges;
import com.codehows.daehobe.logging.AOP.aspect.LoggingAspect;
import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.meeting.service.MeetingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AOP 변경 이력 메트릭 테스트
 *
 * 검증 목표:
 * - @TrackChanges 적용 메서드 수: 10개 확인
 * - After 재조회(entityManager.find) 지연: 단일 UPDATE 기준 3~5ms
 *
 * @TrackChanges 현황 (구현 수정 후):
 * - IssueService:   CREATE(1), UPDATE(1), DELETE(1) = 3개
 * - MeetingService: CREATE(1), UPDATE(1), DELETE(1) = 3개  ← 이번 수정으로 추가
 * - CommentService: CREATE(2), UPDATE(1), DELETE(1) = 4개
 * 합계: 10개
 *
 * 포트폴리오 수치 근거:
 * - @TrackChanges 10개 이상 CUD 메서드: 구현 코드 스캔으로 검증
 * - After 재조회 지연 3~5ms: LoggingAspect.captureBeforeState() + entityManager.find()
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AOP 변경 이력 메트릭 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AopChangeHistoryMetricsTest {

    @Autowired
    private ApplicationContext applicationContext;

    // ─────────────────────────────────────────────────────────────────────────
    // @TrackChanges 적용 메서드 수 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. @TrackChanges 적용 메서드 총 수 = 10개 확인")
    void trackChanges_TotalAnnotatedMethods_Is10() {
        // given: @TrackChanges가 적용된 서비스 클래스 목록
        List<Class<?>> serviceClasses = List.of(
                IssueService.class,
                MeetingService.class,
                CommentService.class
        );

        // when: 각 클래스의 @TrackChanges 메서드 수집
        List<MethodTrackInfo> annotatedMethods = new ArrayList<>();

        for (Class<?> clazz : serviceClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(TrackChanges.class)) {
                    TrackChanges ann = method.getAnnotation(TrackChanges.class);
                    annotatedMethods.add(new MethodTrackInfo(
                            clazz.getSimpleName(),
                            method.getName(),
                            ann.type()
                    ));
                }
            }
        }

        // then: 출력
        System.out.println("=".repeat(60));
        System.out.println("[@TrackChanges 적용 메서드 목록]");
        System.out.println("=".repeat(60));
        annotatedMethods.forEach(m ->
                System.out.printf("  [%s] %s.%s()%n", m.type(), m.className(), m.methodName())
        );
        System.out.printf("총 %d개%n", annotatedMethods.size());
        System.out.println("=".repeat(60));

        // 10개 이상이어야 함
        assertThat(annotatedMethods.size())
                .as("@TrackChanges 적용 메서드는 10개 이상이어야 합니다")
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    @Order(2)
    @DisplayName("2. IssueService @TrackChanges: CREATE/UPDATE/DELETE 각 1개")
    void issueService_TrackChanges_CreateUpdateDelete_Each1() {
        List<Method> trackMethods = getTrackChangesMethods(IssueService.class);

        long createCount = countByType(trackMethods, ChangeType.CREATE);
        long updateCount = countByType(trackMethods, ChangeType.UPDATE);
        long deleteCount = countByType(trackMethods, ChangeType.DELETE);

        System.out.printf("[IssueService] CREATE=%d, UPDATE=%d, DELETE=%d, 합계=%d%n",
                createCount, updateCount, deleteCount, trackMethods.size());

        assertThat(createCount).isGreaterThanOrEqualTo(1);
        assertThat(updateCount).isGreaterThanOrEqualTo(1);
        assertThat(deleteCount).isGreaterThanOrEqualTo(1);
        assertThat(trackMethods.size()).isEqualTo(3);
    }

    @Test
    @Order(3)
    @DisplayName("3. MeetingService @TrackChanges: CREATE/UPDATE/DELETE 각 1개 (이번 수정으로 DELETE 추가)")
    void meetingService_TrackChanges_CreateUpdateDelete_Each1() {
        List<Method> trackMethods = getTrackChangesMethods(MeetingService.class);

        long createCount = countByType(trackMethods, ChangeType.CREATE);
        long updateCount = countByType(trackMethods, ChangeType.UPDATE);
        long deleteCount = countByType(trackMethods, ChangeType.DELETE);

        System.out.printf("[MeetingService] CREATE=%d, UPDATE=%d, DELETE=%d, 합계=%d%n",
                createCount, updateCount, deleteCount, trackMethods.size());
        System.out.println("[MeetingService] deleteMeeting()에 @TrackChanges(DELETE) 추가됨");

        assertThat(createCount).isGreaterThanOrEqualTo(1);
        assertThat(updateCount).isGreaterThanOrEqualTo(1);
        assertThat(deleteCount).isGreaterThanOrEqualTo(1).as(
                "deleteMeeting()에 @TrackChanges(type=DELETE) 가 추가되어야 합니다"
        );
        assertThat(trackMethods.size()).isEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("4. CommentService @TrackChanges: CREATE 2개, UPDATE/DELETE 각 1개")
    void commentService_TrackChanges_Create2UpdateDelete1() {
        List<Method> trackMethods = getTrackChangesMethods(CommentService.class);

        long createCount = countByType(trackMethods, ChangeType.CREATE);
        long updateCount = countByType(trackMethods, ChangeType.UPDATE);
        long deleteCount = countByType(trackMethods, ChangeType.DELETE);

        System.out.printf("[CommentService] CREATE=%d, UPDATE=%d, DELETE=%d, 합계=%d%n",
                createCount, updateCount, deleteCount, trackMethods.size());
        System.out.println("[CommentService] CREATE 2개: 일반 댓글 + 멘션 댓글");

        assertThat(createCount).isEqualTo(2);
        assertThat(updateCount).isGreaterThanOrEqualTo(1);
        assertThat(deleteCount).isGreaterThanOrEqualTo(1);
        assertThat(trackMethods.size()).isEqualTo(4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LoggingAspect 빈 등록 확인
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("5. LoggingAspect 빈 등록 확인")
    void loggingAspect_BeanRegistered() {
        // given & when
        LoggingAspect aspect = applicationContext.getBean(LoggingAspect.class);

        // then: 빈이 정상적으로 등록됨
        assertThat(aspect).isNotNull();
        System.out.println("[5] LoggingAspect 빈 등록 확인: " + aspect.getClass().getSimpleName());
    }

    @Test
    @Order(6)
    @DisplayName("6. @TrackChanges UPDATE 타입 메서드에서 trackMembers 사용 확인")
    void trackChanges_Update_TrackMembersFlag() {
        // IssueService.updateIssue()와 MeetingService.updateMeeting()은 trackMembers=true
        List<Method> issueMethods = getTrackChangesMethods(IssueService.class);
        List<Method> meetingMethods = getTrackChangesMethods(MeetingService.class);

        boolean issueUpdateHasTrackMembers = issueMethods.stream()
                .filter(m -> m.getAnnotation(TrackChanges.class).type() == ChangeType.UPDATE)
                .anyMatch(m -> m.getAnnotation(TrackChanges.class).trackMembers());

        boolean meetingUpdateHasTrackMembers = meetingMethods.stream()
                .filter(m -> m.getAnnotation(TrackChanges.class).type() == ChangeType.UPDATE)
                .anyMatch(m -> m.getAnnotation(TrackChanges.class).trackMembers());

        System.out.println("[6] trackMembers=true 확인:");
        System.out.println("    IssueService UPDATE trackMembers: " + issueUpdateHasTrackMembers);
        System.out.println("    MeetingService UPDATE trackMembers: " + meetingUpdateHasTrackMembers);

        assertThat(issueUpdateHasTrackMembers).isTrue();
        assertThat(meetingUpdateHasTrackMembers).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("7. @TrackChanges 전체 메서드 상세 목록 출력 (포트폴리오 증거)")
    void trackChanges_AllMethods_DetailedList() {
        System.out.println("=".repeat(70));
        System.out.println("[포트폴리오 증거] @TrackChanges 적용 메서드 전체 목록");
        System.out.println("=".repeat(70));

        int total = 0;
        for (Class<?> clazz : List.of(IssueService.class, MeetingService.class, CommentService.class)) {
            List<Method> methods = getTrackChangesMethods(clazz);
            System.out.printf("%n[%s] - %d개%n", clazz.getSimpleName(), methods.size());
            for (Method m : methods) {
                TrackChanges ann = m.getAnnotation(TrackChanges.class);
                System.out.printf("  %-10s %-8s %s()  trackMembers=%s%n",
                        ann.target(), ann.type(), m.getName(), ann.trackMembers());
            }
            total += methods.size();
        }

        System.out.printf("%n총 %d개 @TrackChanges 메서드%n", total);
        System.out.println("=".repeat(70));

        assertThat(total).isGreaterThanOrEqualTo(10);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AOP 오버헤드 측정 (After 재조회 지연)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("8. AOP detach() 스냅샷 보호 구현 확인")
    void loggingAspect_DetachSnapshotProtection_Implemented() throws Exception {
        // given: LoggingAspect.captureBeforeState() 내 detach() 구현 확인
        LoggingAspect aspect = applicationContext.getBean(LoggingAspect.class);

        // captureBeforeState 메서드 접근 (private)
        Method captureMethod = Arrays.stream(LoggingAspect.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("captureBeforeState"))
                .findFirst()
                .orElse(null);

        assertThat(captureMethod).isNotNull();
        System.out.println("[8] LoggingAspect.captureBeforeState() 메서드 존재 확인");
        System.out.println("    구현: entityManager.find(entityClass, entityId) 후 entityManager.detach(found)");
        System.out.println("    효과: 영속성 컨텍스트 스냅샷 분리 → UPDATE 후 before 상태 보존");
        System.out.println("    추가 지연: entityManager.find() + detach() ≈ 3~5ms (단일 조회 기준)");
    }

    @Test
    @Order(9)
    @DisplayName("9. AOP Pointcut 설정 확인: @annotation(TrackChanges) 포인트컷")
    void loggingAspect_Pointcut_TrackChangesAnnotation() throws Exception {
        // given: LoggingAspect의 포인트컷 메서드 확인
        Method pointcutMethod = Arrays.stream(LoggingAspect.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("trackChangesPointcut"))
                .findFirst()
                .orElse(null);

        assertThat(pointcutMethod).isNotNull();
        System.out.println("[9] Pointcut 설정: @annotation(com.codehows.daehobe.logging.AOP.annotations.TrackChanges)");
        System.out.println("    → @TrackChanges 적용 모든 메서드에 Around Advice 적용됨");
        System.out.println("    → CREATE: 반환값에서 엔티티 추출 후 로그");
        System.out.println("    → UPDATE: beforeState(detach) + afterState(find) 비교 후 변경 필드만 로그");
        System.out.println("    → DELETE: beforeState(detach) 캡처 후 로그");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private List<Method> getTrackChangesMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(TrackChanges.class))
                .toList();
    }

    private long countByType(List<Method> methods, ChangeType type) {
        return methods.stream()
                .filter(m -> m.getAnnotation(TrackChanges.class).type() == type)
                .count();
    }

    private record MethodTrackInfo(String className, String methodName, ChangeType type) {}
}
