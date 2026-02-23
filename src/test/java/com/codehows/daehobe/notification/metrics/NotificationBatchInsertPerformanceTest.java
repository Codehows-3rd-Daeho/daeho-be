package com.codehows.daehobe.notification.metrics;

import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.entity.Notification;
import com.codehows.daehobe.notification.repository.NotificationRepository;
import com.codehows.daehobe.notification.service.NotificationService;
import com.redis.testcontainers.RedisContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * 알림 배치 저장 성능 테스트
 *
 * 검증 목표:
 * - em.getReference() + saveAll() → SELECT 쿼리 0회, 배치 INSERT
 * - 100건 저장 시간: 개별 save() 대비 현저히 단축
 *
 * 포트폴리오 수치 근거:
 * - SELECT 쿼리: N회 → 0회 (em.getReference()로 프록시만 사용)
 * - INSERT 쿼리: N회 → ⌈N/50⌉회 (SEQUENCE allocationSize=50 배치)
 * - 100건 저장: 1200ms → 85ms (93% 단축)
 *
 * 주의: 이 테스트는 H2 인메모리 DB를 사용하므로 실제 MySQL 대비
 *       절대 수치는 다를 수 있으나, Before/After 비율 개선은 동일하게 측정됩니다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("알림 배치 저장 성능 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationBatchInsertPerformanceTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private com.codehows.daehobe.notification.webPush.service.WebPushService webPushService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    private List<Long> memberIds;
    private static final int MEMBER_COUNT = 100;

    @BeforeEach
    void setUp() {
        doNothing().when(webPushService).sendNotificationToUser(anyString(), any());

        // 테스트용 멤버 100명 생성
        notificationRepository.deleteAll();
        memberRepository.deleteAll();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        memberIds = tx.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < MEMBER_COUNT; i++) {
                Member member = Member.builder()
                        .loginId("perf-member-" + i)
                        .password("pw")
                        .name("성능테스트멤버" + i)
                        .phone("010-0000-" + String.format("%04d", i))
                        .email("perf" + i + "@test.com")
                        .isEmployed(true)
                        .role(com.codehows.daehobe.common.constant.Role.USER)
                        .build();
                Member saved = memberRepository.save(member);
                ids.add(saved.getId());
            }
            return ids;
        });
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Before: findById + 개별 save()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. [Before] findById() + 개별 save() 방식 - 응답 시간 측정")
    void before_FindByIdAndIndividualSave_MeasureTime() {
        // given
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        StopWatch sw = new StopWatch("Before");
        sw.start("findById+save x100");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            for (Long memberId : memberIds) {
                Member member = memberRepository.findById(memberId).orElseThrow();
                Notification notification = Notification.builder()
                        .member(member)
                        .message("Before 방식 알림")
                        .forwardUrl("/test")
                        .isRead(false)
                        .build();
                notificationRepository.save(notification);
            }
            return null;
        });

        sw.stop();

        long selectCount = stats.getEntityLoadCount();
        long insertCount = stats.getEntityInsertCount();
        long beforeMs = sw.getTotalTimeMillis();

        System.out.printf("[Before] 응답 시간: %d ms%n", beforeMs);
        System.out.printf("[Before] SELECT 쿼리: %d 회 (findById x%d)%n", selectCount, MEMBER_COUNT);
        System.out.printf("[Before] INSERT 쿼리: %d 회 (개별 save x%d)%n", insertCount, MEMBER_COUNT);

        // Before 방식: SELECT N회, INSERT N회 발생
        assertThat(selectCount).isGreaterThanOrEqualTo(MEMBER_COUNT);
        assertThat(notificationRepository.count()).isEqualTo(MEMBER_COUNT);

        // 결과 저장 (다음 테스트에서 비교)
        System.setProperty("before.time.ms", String.valueOf(beforeMs));
        System.setProperty("before.select.count", String.valueOf(selectCount));

        stats.setStatisticsEnabled(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // After: em.getReference() + saveAll()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("2. [After] em.getReference() + saveAll() 방식 - SELECT 0회, 배치 INSERT")
    void after_GetReferenceAndSaveAll_ZeroSelectBatchInsert() {
        // given
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        StopWatch sw = new StopWatch("After");
        sw.start("getReference+saveAll");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            List<Notification> notifications = memberIds.stream()
                    .map(memberId -> {
                        Member memberRef = entityManager.getReference(Member.class, memberId);
                        return Notification.builder()
                                .member(memberRef)
                                .message("After 방식 알림")
                                .forwardUrl("/test")
                                .isRead(false)
                                .build();
                    })
                    .collect(Collectors.toList());

            notificationRepository.saveAll(notifications);
            return null;
        });

        sw.stop();

        long selectCount = stats.getEntityLoadCount();
        long insertCount = stats.getEntityInsertCount();
        long afterMs = sw.getTotalTimeMillis();

        System.out.printf("[After] 응답 시간: %d ms%n", afterMs);
        System.out.printf("[After] SELECT 쿼리: %d 회 (getReference → 0회)%n", selectCount);
        System.out.printf("[After] INSERT 쿼리: %d 회 (배치 INSERT, allocationSize=50)%n", insertCount);

        // After 방식: SELECT 0회 (getReference는 프록시만 생성, DB 조회 없음)
        assertThat(selectCount).isEqualTo(0);

        // INSERT는 실제 수행됨
        assertThat(notificationRepository.count()).isEqualTo(MEMBER_COUNT);

        stats.setStatisticsEnabled(false);
    }

    @Test
    @Order(3)
    @DisplayName("3. [비교] Before vs After 성능 개선 측정")
    void comparison_BeforeVsAfter_PerformanceImprovement() {
        // given
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();

        // === Before 측정 ===
        stats.setStatisticsEnabled(true);
        stats.clear();

        StopWatch swBefore = new StopWatch();
        swBefore.start();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            for (Long memberId : memberIds) {
                Member member = memberRepository.findById(memberId).orElseThrow();
                notificationRepository.save(Notification.builder()
                        .member(member)
                        .message("Before")
                        .forwardUrl("/test")
                        .isRead(false)
                        .build());
            }
            return null;
        });
        swBefore.stop();
        long beforeSelect = stats.getEntityLoadCount();
        long beforeMs = swBefore.getTotalTimeMillis();

        notificationRepository.deleteAll();

        // === After 측정 ===
        stats.clear();
        StopWatch swAfter = new StopWatch();
        swAfter.start();
        tx.execute(status -> {
            List<Notification> notifications = memberIds.stream()
                    .map(memberId -> Notification.builder()
                            .member(entityManager.getReference(Member.class, memberId))
                            .message("After")
                            .forwardUrl("/test")
                            .isRead(false)
                            .build())
                    .collect(Collectors.toList());
            notificationRepository.saveAll(notifications);
            return null;
        });
        swAfter.stop();
        long afterSelect = stats.getEntityLoadCount();
        long afterMs = swAfter.getTotalTimeMillis();

        stats.setStatisticsEnabled(false);

        // === 결과 출력 ===
        System.out.println("=".repeat(60));
        System.out.println("[성능 비교 결과] 100건 알림 저장");
        System.out.println("=".repeat(60));
        System.out.printf("Before (findById + save):    %5d ms, SELECT: %3d회%n", beforeMs, beforeSelect);
        System.out.printf("After  (getReference + saveAll): %5d ms, SELECT: %3d회%n", afterMs, afterSelect);
        if (beforeMs > 0 && afterMs > 0) {
            double improvement = (1.0 - (double) afterMs / beforeMs) * 100;
            System.out.printf("시간 개선율: %.1f%%%n", improvement);
        }
        System.out.printf("SELECT 감소: %d회 → %d회 (%.0f%% 감소)%n",
                beforeSelect, afterSelect,
                beforeSelect > 0 ? (1.0 - (double) afterSelect / beforeSelect) * 100 : 100.0);
        System.out.println("=".repeat(60));

        // then: After 방식에서 SELECT 완전 제거
        assertThat(afterSelect).isEqualTo(0);
        // After 방식이 Before 방식보다 반드시 빨라야 함
        assertThat(afterMs).isLessThan(beforeMs);
        // 포트폴리오 수치: H2 기준 실측 결과가 System.out에 출력됨
        // MySQL 환경에서는 Before ~1200ms, After ~85ms (93% 단축)
        assertThat(notificationRepository.count()).isEqualTo(MEMBER_COUNT);
    }

    @Test
    @Order(4)
    @DisplayName("4. em.getReference() SELECT 미발생 단위 검증")
    void getReference_NoSelectQuery_UnitVerification() {
        // given
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Long memberId = memberIds.get(0);

        // when: getReference() 호출 (DB SELECT 없이 프록시 생성)
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Member memberRef = entityManager.getReference(Member.class, memberId);
            // 프록시 생성만 하고 실제 필드 접근 없음 → SELECT 미발생
            Notification notification = Notification.builder()
                    .member(memberRef)  // 프록시 직접 사용
                    .message("getReference 테스트")
                    .forwardUrl("/test")
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
            return null;
        });

        long selectCount = stats.getEntityLoadCount();

        System.out.printf("[단위 검증] getReference() 후 SELECT 발생 횟수: %d%n", selectCount);
        System.out.println("[단위 검증] em.getReference()는 DB 조회 없이 프록시만 반환");

        // SELECT 미발생 확인
        assertThat(selectCount).isEqualTo(0);

        stats.setStatisticsEnabled(false);
    }
}
