package com.codehows.daehobe.stt.service.scheduler;

import com.codehows.daehobe.stt.service.processor.DistributedLockManager;
import com.codehows.daehobe.stt.service.processor.SttJobProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.PROCESSING_LOCK_KEY;
import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.STT_PROCESSING_SET;

@Component
@Slf4j
public class ProcessingScheduler extends AbstractSttScheduler {

    private final SttJobProcessor jobExecutor;

    public ProcessingScheduler(
            RedisTemplate<String, String> redisTemplate,
            DistributedLockManager lockManager,
            SttJobProcessor jobExecutor) {
        super("STT-Processing", redisTemplate, lockManager);
        this.jobExecutor = jobExecutor;
    }

    @Override
    protected String getSchedulerName() {
        return "Processing";
    }

    @Override
    protected String getDistributedLockKey() {
        return PROCESSING_LOCK_KEY;
    }

    @Override
    protected long getInitialDelay() {
        return 0;
    }

    @Override
    protected long getPeriod() {
        return 2;  // 2초 주기
    }

    @Override
    protected void processTask() {
        Set<String> processingSttIds = redisTemplate.opsForSet()
                .members(STT_PROCESSING_SET);

        if (processingSttIds == null || processingSttIds.isEmpty()) {
            return;
        }

        log.info("Processing {} STT tasks", processingSttIds.size());

        List<Mono<Object>> tasks = processingSttIds.stream()
                .map(Long::valueOf)
                .map(jobExecutor::processSingleSttJob)
                .toList();

        Flux.merge(tasks)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("Some STT tasks failed", e);
                    return Mono.empty();
                })
                .blockLast();
    }
}