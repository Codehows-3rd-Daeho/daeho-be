package com.codehows.daehobe.stt.service.scheduler;

import com.codehows.daehobe.stt.service.processor.DistributedLockManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class AbstractSttScheduler {

    protected final RedisTemplate<String, String> redisTemplate;
    private final DistributedLockManager lockManager;

    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ScheduledFuture<?>> currentTask = new AtomicReference<>();
    private final ReentrantLock localLock = new ReentrantLock();
    private volatile SchedulerState state = SchedulerState.STOPPED;

    private enum SchedulerState {
        STOPPED, STARTING, RUNNING, STOPPING
    }

    protected AbstractSttScheduler(
            String schedulerName,
            RedisTemplate<String, String> redisTemplate,
            DistributedLockManager lockManager) {
        this.redisTemplate = redisTemplate;
        this.lockManager = lockManager;
        this.scheduler = createScheduler(schedulerName);
    }

    private ScheduledExecutorService createScheduler(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    // 템플릿 메서드 패턴
    public void start() {
        String lockKey = getDistributedLockKey();

        if (!lockManager.acquireLock(lockKey)) {
            log.info("Another instance is running {} scheduler", getSchedulerName());
            return;
        }

        localLock.lock();
        try {
            if (state == SchedulerState.RUNNING || state == SchedulerState.STARTING) {
                log.info("{} scheduler already running", getSchedulerName());
                return;
            }

            state = SchedulerState.STARTING;
            log.info("Starting {} scheduler...", getSchedulerName());

            cancelCurrentTaskSafely();

            ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(
                    this::executeTask,
                    getInitialDelay(),
                    getPeriod(),
                    TimeUnit.SECONDS
            );

            currentTask.set(task);
            state = SchedulerState.RUNNING;

            log.info("{} scheduler started successfully.", getSchedulerName());

        } catch (Exception e) {
            log.error("Failed to start {} scheduler", getSchedulerName(), e);
            state = SchedulerState.STOPPED;
            throw e;
        } finally {
            localLock.unlock();
            lockManager.releaseLock(lockKey);
        }
    }

    public void stop() {
        localLock.lock();
        try {
            if (state == SchedulerState.STOPPED) {
                log.info("{} scheduler already stopped", getSchedulerName());
                return;
            }

            state = SchedulerState.STOPPING;
            log.info("Stopping {} scheduler...", getSchedulerName());

            cancelCurrentTaskSafely();

            state = SchedulerState.STOPPED;
            log.info("{} scheduler stopped successfully.", getSchedulerName());

        } finally {
            localLock.unlock();
        }
    }

    private void executeTask() {
        if (state != SchedulerState.RUNNING) {
            log.debug("{} scheduler not in RUNNING state", getSchedulerName());
            return;
        }

        try {
            processTask();
        } catch (Exception e) {
            log.error("Error in {} scheduler task execution", getSchedulerName(), e);
        }
    }

    private void cancelCurrentTaskSafely() {
        ScheduledFuture<?> task = currentTask.getAndSet(null);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            try {
                task.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("{} task did not finish within timeout", getSchedulerName());
                task.cancel(true);
            } catch (Exception e) {
                log.debug("{} task cancellation completed", getSchedulerName());
            }
        }
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("{} scheduler did not terminate in time", getSchedulerName());
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("{} scheduler shutdown interrupted", getSchedulerName());
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {
        return state == SchedulerState.RUNNING;
    }

    // 하위 클래스에서 구현해야 하는 메서드들
    protected abstract String getSchedulerName();
    protected abstract String getDistributedLockKey();
    protected abstract long getInitialDelay();
    protected abstract long getPeriod();
    protected abstract void processTask();
}