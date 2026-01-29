package com.codehows.daehobe.stt.integration;

import com.codehows.daehobe.stt.service.processing.DistributedLockManager;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Redis 분산락 통합 테스트
 *
 * 테스트 케이스:
 * 1. 단일 락 획득/해제 정상 동작
 * 2. 동시 락 획득 경쟁 (10 스레드 → 1개만 성공)
 * 3. 여러 인스턴스 시뮬레이션 (같은 STT ID에 대한 중복 처리 방지)
 * 4. TTL 만료 시 락 자동 해제
 * 5. 크래시 복구 시나리오 (TTL 기반)
 */
@Testcontainers
@DisplayName("Redis 분산락 통합 테스트")
class DistributedLockIntegrationTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private RedisTemplate<String, String> redisTemplate;
    private DistributedLockManager lockManager;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379)
        );
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        lockManager = new DistributedLockManager(redisTemplate);

        // 테스트 간 격리를 위해 모든 키 삭제
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("단일 락 획득/해제 정상 동작")
    void acquireAndReleaseLock_SingleThread_Success() {
        // given
        String lockKey = "stt:processor:lock:test:1";

        // when - 락 획득
        boolean acquired = lockManager.acquireLock(lockKey);

        // then
        assertThat(acquired).isTrue();
        assertThat(redisTemplate.hasKey(lockKey)).isTrue();

        // when - 락 해제
        lockManager.releaseLock(lockKey);

        // then
        assertThat(redisTemplate.hasKey(lockKey)).isFalse();
    }

    @Test
    @DisplayName("동시 락 획득 경쟁 - 10 스레드 중 1개만 성공")
    void acquireLock_ConcurrentThreads_OnlyOneSucceeds() throws InterruptedException {
        // given
        String lockKey = "stt:processor:lock:concurrent:1";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작
                    boolean acquired = lockManager.acquireLock(lockKey);
                    if (acquired) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 시작
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 정확히 1개만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    @DisplayName("여러 인스턴스 시뮬레이션 - 같은 STT ID에 대한 중복 처리 방지")
    void acquireLock_MultipleInstances_PreventsDuplicateProcessing() throws InterruptedException {
        // given
        String sttId = "123";
        String lockKey = "stt:processor:lock:encoding:" + sttId;
        int instanceCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(instanceCount);
        List<String> processedBy = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(instanceCount);

        // when - 5개의 인스턴스가 동시에 같은 STT를 처리하려고 시도
        for (int i = 0; i < instanceCount; i++) {
            final String instanceId = "instance-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (lockManager.acquireLock(lockKey)) {
                        processedBy.add(instanceId);
                        // 처리 시뮬레이션
                        Thread.sleep(100);
                        lockManager.releaseLock(lockKey);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 정확히 1개의 인스턴스만 처리
        assertThat(processedBy).hasSize(1);
    }

    @Test
    @DisplayName("TTL 만료 시 락 자동 해제")
    void acquireLock_TTLExpiry_AutoRelease() {
        // given
        String lockKey = "stt:processor:lock:ttl-test:1";
        long shortTtl = 1; // 1초

        // when
        boolean acquired = lockManager.acquireLock(lockKey, shortTtl);

        // then - 락 획득 성공
        assertThat(acquired).isTrue();
        assertThat(redisTemplate.hasKey(lockKey)).isTrue();

        // TTL 만료 후 다른 클라이언트가 락 획득 가능
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    // 이전 락이 만료되었는지 확인
                    return !Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
                });

        // 새로운 락 획득 시도
        boolean reacquired = lockManager.acquireLock(lockKey);
        assertThat(reacquired).isTrue();

        // cleanup
        lockManager.releaseLock(lockKey);
    }

    @Test
    @DisplayName("크래시 복구 시나리오 - TTL 기반 데드락 방지")
    void acquireLock_CrashRecovery_TTLPreventsDeadlock() {
        // given
        String lockKey = "stt:processor:lock:crash:1";
        long shortTtl = 2; // 2초

        // 첫 번째 인스턴스가 락을 획득하고 크래시 (해제하지 않음)
        boolean firstAcquired = lockManager.acquireLock(lockKey, shortTtl);
        assertThat(firstAcquired).isTrue();

        // 두 번째 인스턴스가 즉시 락 획득 시도 - 실패해야 함
        boolean secondAcquiredImmediately = lockManager.acquireLock(lockKey, shortTtl);
        assertThat(secondAcquiredImmediately).isFalse();

        // TTL 만료 후 두 번째 인스턴스가 락 획득 가능
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> lockManager.acquireLock(lockKey, shortTtl));

        // cleanup
        lockManager.releaseLock(lockKey);
    }

    @Test
    @DisplayName("setIfAbsent 원자적 락 획득 검증")
    void acquireLock_AtomicOperation_Verified() throws InterruptedException {
        // given
        String lockKey = "stt:processor:lock:atomic:1";
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when - 100개 스레드가 동시에 락 획득 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (lockManager.acquireLock(lockKey)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 원자적 연산으로 정확히 1개만 성공
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 락 키는 독립적으로 동작")
    void acquireLock_DifferentKeys_Independent() {
        // given
        String lockKey1 = "stt:processor:lock:encoding:1";
        String lockKey2 = "stt:processor:lock:encoding:2";
        String lockKey3 = "stt:processor:lock:processing:1";

        // when
        boolean acquired1 = lockManager.acquireLock(lockKey1);
        boolean acquired2 = lockManager.acquireLock(lockKey2);
        boolean acquired3 = lockManager.acquireLock(lockKey3);

        // then - 모든 키에 대해 독립적으로 락 획득 가능
        assertThat(acquired1).isTrue();
        assertThat(acquired2).isTrue();
        assertThat(acquired3).isTrue();

        // cleanup
        lockManager.releaseLock(lockKey1);
        lockManager.releaseLock(lockKey2);
        lockManager.releaseLock(lockKey3);
    }
}
