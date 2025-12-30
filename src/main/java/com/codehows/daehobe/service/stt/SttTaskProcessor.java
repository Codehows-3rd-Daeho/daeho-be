package com.codehows.daehobe.service.stt;

import com.codehows.daehobe.dto.file.FileDto;
import com.codehows.daehobe.dto.stt.STTResponseDto;
import com.codehows.daehobe.dto.stt.STTDto;
import com.codehows.daehobe.dto.stt.SummaryResponseDto;
import com.codehows.daehobe.entity.file.File;
import com.codehows.daehobe.entity.stt.STT;
import com.codehows.daehobe.repository.stt.STTRepository;
import com.codehows.daehobe.service.file.FileService;
import com.codehows.daehobe.utils.DataSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttTaskProcessor {

    private final SttJobExecutor jobExecutor;
    private final RedisTemplate<String, String> redisTemplate;

    // 스케줄러를 데몬 스레드로 생성하여 앱 종료 시 자동 정리
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "STT-Processor");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<ScheduledFuture<?>> currentTask = new AtomicReference<>();
    private final ReentrantLock processorLock = new ReentrantLock();

    public static final String STT_PROCESSING_SET = "stt:processing";
    public static final String STT_SUMMARIZING_SET = "stt:summarizing";
    public static final String STT_STATUS_HASH_PREFIX = "stt:status:";
    public static final String STT_PROCESSOR_LOCK = "stt:processor:lock";

    private volatile ProcessorState state = ProcessorState.STOPPED;

    private enum ProcessorState {
        STOPPED,    // 완전 중지
        STARTING,   // 시작 중
        RUNNING,    // 실행 중
        STOPPING    // 중지 중
    }

    @Async
    public void startSmartLoop() {
        String lockKey = STT_PROCESSOR_LOCK;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", 5, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.info("Another instance is starting the processor");
            return;
        }

        try {
            startSmartLoopInternal();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void startSmartLoopInternal() {
        processorLock.lock();
        try {
            // 이미 실행 중이거나 시작 중이면 무시
            if (state == ProcessorState.RUNNING || state == ProcessorState.STARTING) {
                log.info("STT processor already running or starting. Current state: {}", state);
                return;
            }

            // 중지 중이면 완전히 중지될 때까지 대기
            if (state == ProcessorState.STOPPING) {
                log.info("Processor is stopping. Please wait and retry.");
                return;
            }

            state = ProcessorState.STARTING;
            log.info("Starting STT smart-loop processor...");

            // 이전 태스크 안전 종료
            cancelCurrentTaskSafely();

            // 새 스케줄 시작
            ScheduledFuture<?> newTask = scheduler.scheduleWithFixedDelay(
                    this::processAllTasksSafely,
                    0,
                    2,
                    TimeUnit.SECONDS
            );

            currentTask.set(newTask);
            state = ProcessorState.RUNNING;
            log.info("STT processor started successfully.");

        } catch (Exception e) {
            log.error("Failed to start STT processor", e);
            state = ProcessorState.STOPPED;
            throw e;
        } finally {
            processorLock.unlock();
        }
    }

    public void stopProcessor() {
        processorLock.lock();
        try {
            if (state == ProcessorState.STOPPED || state == ProcessorState.STOPPING) {
                log.info("Processor already stopped or stopping");
                return;
            }

            state = ProcessorState.STOPPING;
            log.info("Stopping STT processor...");

            cancelCurrentTaskSafely();

            state = ProcessorState.STOPPED;
            log.info("STT processor stopped successfully.");

        } finally {
            processorLock.unlock();
        }
    }

    private void cancelCurrentTaskSafely() {
        ScheduledFuture<?> task = currentTask.getAndSet(null);
        if (task != null && !task.isCancelled()) {
            task.cancel(false); // graceful cancel
            try {
                // 최대 5초 대기하여 현재 실행 중인 작업 완료 확인
                task.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Current task did not finish within timeout");
                task.cancel(true); // force cancel
            } catch (Exception e) {
                log.debug("Task cancellation completed: {}", e.getMessage());
            }
        }
    }

    private void processAllTasksSafely() {
        // 상태가 RUNNING이 아니면 실행하지 않음
        if (state != ProcessorState.RUNNING) {
            log.debug("Processor not in RUNNING state: {}", state);
            return;
        }

        try {
            processAllTasks();
        } catch (Exception e) {
            log.error("Error in processAllTasks, but continuing processor", e);
            // 예외가 발생해도 스케줄러는 계속 실행됨
        }
    }

    private void processAllTasks() {
        Long processingCount = redisTemplate.opsForSet().size(STT_PROCESSING_SET);
        Long summarizingCount = redisTemplate.opsForSet().size(STT_SUMMARIZING_SET);

        processingCount = processingCount != null ? processingCount : 0L;
        summarizingCount = summarizingCount != null ? summarizingCount : 0L;

        if (processingCount == 0 && summarizingCount == 0) {
            log.info("All task queues are empty. Auto-stopping processor.");
            stopProcessor();
            return;
        }

        log.debug("Processing cycle - STT: {}, Summary: {}", processingCount, summarizingCount);

        // STT 처리 - jobExecutor 사용
        Set<String> processingSttIds = redisTemplate.opsForSet().members(STT_PROCESSING_SET);
        if (processingSttIds != null && !processingSttIds.isEmpty()) {
            log.info("Processing {} STT tasks", processingSttIds.size());
            processingSttIds.forEach(sttIdStr -> {
                try {
                    Long sttId = Long.valueOf(sttIdStr);
                    jobExecutor.processSingleSttJob(sttId);  // 변경
                } catch (NumberFormatException e) {
                    log.error("Invalid sttId format: {}", sttIdStr);
                    redisTemplate.opsForSet().remove(STT_PROCESSING_SET, sttIdStr);
                } catch (Exception e) {
                    log.error("Error processing STT task: {}", sttIdStr, e);
                }
            });
        }

        // 요약 처리 - jobExecutor 사용
        Set<String> summarizingSttIds = redisTemplate.opsForSet().members(STT_SUMMARIZING_SET);
        if (summarizingSttIds != null && !summarizingSttIds.isEmpty()) {
            log.info("Processing {} summary tasks", summarizingSttIds.size());
            summarizingSttIds.forEach(sttIdStr -> {
                try {
                    Long sttId = Long.valueOf(sttIdStr);
                    jobExecutor.processSingleSummaryJob(sttId);  // 변경
                } catch (NumberFormatException e) {
                    log.error("Invalid sttId format: {}", sttIdStr);
                    redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, sttIdStr);
                } catch (Exception e) {
                    log.error("Error processing summary task: {}", sttIdStr, e);
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down STT processor...");
        stopProcessor();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}