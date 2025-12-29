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

    private final STTRepository sttRepository;
    private final STTService sttService;
    private final RedisTemplate<String, String> redisTemplate;

    // 스케줄러를 데몬 스레드로 생성하여 앱 종료 시 자동 정리
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "STT-Processor");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<ScheduledFuture<?>> currentTask = new AtomicReference<>();
    private final ReentrantLock processorLock = new ReentrantLock();

    @Lazy
    @Autowired
    private SttTaskProcessor self;

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

    /**
     * 스마트 루프 시작 - 중복 호출에 안전
     */
    @Async
    public void startSmartLoop() {
        // 분산 락으로 여러 인스턴스에서 동시 시작 방지 (선택사항)
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

    /**
     * 프로세서 강제 중지
     */
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

    /**
     * 예외에 안전한 작업 처리 래퍼
     */
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

        // null 체크 추가
        processingCount = processingCount != null ? processingCount : 0L;
        summarizingCount = summarizingCount != null ? summarizingCount : 0L;

        // 큐가 모두 비어있으면 자동 중지
        if (processingCount == 0 && summarizingCount == 0) {
            log.info("All task queues are empty. Auto-stopping processor.");
            stopProcessor();
            return;
        }

        log.debug("Processing cycle - STT: {}, Summary: {}", processingCount, summarizingCount);

        // STT 처리
        Set<String> processingSttIds = redisTemplate.opsForSet().members(STT_PROCESSING_SET);
        if (processingSttIds != null && !processingSttIds.isEmpty()) {
            log.info("Processing {} STT tasks", processingSttIds.size());
            processingSttIds.forEach(sttIdStr -> {
                try {
                    Long sttId = Long.valueOf(sttIdStr);
                    self.processSingleSttJob(sttId);
                } catch (NumberFormatException e) {
                    log.error("Invalid sttId format: {}", sttIdStr);
                    redisTemplate.opsForSet().remove(STT_PROCESSING_SET, sttIdStr);
                } catch (Exception e) {
                    log.error("Error processing STT task: {}", sttIdStr, e);
                    // 에러가 발생해도 다음 작업 계속 진행
                }
            });
        }

        // 요약 처리
        Set<String> summarizingSttIds = redisTemplate.opsForSet().members(STT_SUMMARIZING_SET);
        if (summarizingSttIds != null && !summarizingSttIds.isEmpty()) {
            log.info("Processing {} summary tasks", summarizingSttIds.size());
            summarizingSttIds.forEach(sttIdStr -> {
                try {
                    Long sttId = Long.valueOf(sttIdStr);
                    self.processSingleSummaryJob(sttId);
                } catch (NumberFormatException e) {
                    log.error("Invalid sttId format: {}", sttIdStr);
                    redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, sttIdStr);
                } catch (Exception e) {
                    log.error("Error processing summary task: {}", sttIdStr, e);
                }
            });
        }
    }

    @Transactional
    public void processSingleSttJob(Long sttId) {
        try {
            STT stt = sttRepository.findById(sttId).orElse(null);
            if (stt == null) {
                log.warn("STT not found: {}", sttId);
                redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
                return;
            }

            if (stt.getStatus() != STT.Status.PROCESSING) {
                log.info("STT {} status changed to {}, removing from queue", sttId, stt.getStatus());
                redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
                return;
            }

            STTResponseDto result = sttService.checkSTTStatus(stt);
            stt.updateContent(result.getContent());

            STTDto cachedStt = STTDto.fromEntity(stt);
            cachedStt.updateProgress(result.getProgress());
            self.cacheSttStatus(cachedStt);

            if (result.isCompleted()) {
                log.info("STT {} completed, transitioning to SUMMARIZING", sttId);
                stt.setStatus(STT.Status.SUMMARIZING);
                stt.updateContent(result.getContent());
                sttRepository.save(stt);

                STTDto finalCachedStt = STTDto.fromEntity(stt);
                finalCachedStt.updateProgress(result.getProgress());
                self.cacheSttStatus(finalCachedStt);

                // 큐 이동을 원자적으로 수행
                redisTemplate.execute((RedisCallback<Object>) connection -> {
                    connection.sRem(STT_PROCESSING_SET.getBytes(), String.valueOf(sttId).getBytes());
                    connection.sAdd(STT_SUMMARIZING_SET.getBytes(), String.valueOf(sttId).getBytes());
                    return null;
                });

                sttService.requestSummary(sttId);
            }
        } catch (Exception e) {
            log.error("Failed to process STT job: {}", sttId, e);
            // 심각한 오류 시 큐에서 제거하여 무한 재시도 방지
            if (isUnrecoverableError(e)) {
                redisTemplate.opsForSet().remove(STT_PROCESSING_SET, String.valueOf(sttId));
            }
        }
    }

    @Transactional
    public void processSingleSummaryJob(Long sttId) {
        try {
            STT stt = sttRepository.findById(sttId).orElse(null);
            if (stt == null) {
                log.warn("STT not found: {}", sttId);
                redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                redisTemplate.delete(STT_STATUS_HASH_PREFIX + sttId);
                return;
            }

            if (stt.getStatus() != STT.Status.SUMMARIZING) {
                log.info("STT {} status changed to {}, removing from queue", sttId, stt.getStatus());
                redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                return;
            }

            if (stt.getSummaryRid() == null) {
                log.warn("STT {} has no summaryRid", sttId);
                redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                return;
            }

            SummaryResponseDto result = sttService.checkSummaryStatus(stt);
            stt.updateSummary(result.getSummaryText());

            STTDto cachedStt = STTDto.fromEntity(stt);
            cachedStt.updateProgress(result.getProgress());
            self.cacheSttStatus(cachedStt);

            if (result.isCompleted()) {
                log.info("Summary {} completed", sttId);
                stt.setStatus(STT.Status.COMPLETED);
                stt.updateSummary(result.getSummaryText());
                sttRepository.save(stt);

                STTDto finalCachedStt = STTDto.fromEntity(stt);
                finalCachedStt.updateProgress(result.getProgress());
                self.cacheSttStatus(finalCachedStt);

                redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
                redisTemplate.expire(STT_STATUS_HASH_PREFIX + sttId, 5, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.error("Failed to process summary job: {}", sttId, e);
            if (isUnrecoverableError(e)) {
                redisTemplate.opsForSet().remove(STT_SUMMARIZING_SET, String.valueOf(sttId));
            }
        }
    }

    public void cacheSttStatus(STTDto stt) {
        try {
            redisTemplate.opsForValue().set(
                    STT_STATUS_HASH_PREFIX + stt.getId(),
                    DataSerializer.serialize(stt),
                    30,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("Failed to cache STT status: {}", stt.getId(), e);
        }
    }

    /**
     * 복구 불가능한 오류인지 판단
     */
    private boolean isUnrecoverableError(Exception e) {
        // 일시적 네트워크 오류, 타임아웃 등은 재시도 가능
        // 데이터 불일치, 유효하지 않은 상태 등은 복구 불가능
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e.getCause() instanceof NumberFormatException;
    }

    /**
     * 현재 프로세서 상태 조회
     */
    public String getProcessorStatus() {
        return String.format("State: %s, Task active: %s, Processing: %d, Summarizing: %d",
                state,
                currentTask.get() != null && !currentTask.get().isDone(),
                redisTemplate.opsForSet().size(STT_PROCESSING_SET),
                redisTemplate.opsForSet().size(STT_SUMMARIZING_SET)
        );
    }

    /**
     * 애플리케이션 종료 시 정리
     */
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