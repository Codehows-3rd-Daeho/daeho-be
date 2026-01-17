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

import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.*;

@Component
@Slf4j
public class SummarizingScheduler extends AbstractSttScheduler {
    private final SttJobProcessor jobExecutor;

    public SummarizingScheduler(
            RedisTemplate<String, String> redisTemplate,
            DistributedLockManager lockManager,
            SttJobProcessor jobExecutor) {
        super("STT-Summarizing", redisTemplate, lockManager);
        this.jobExecutor = jobExecutor;
    }

    @Override
    protected String getSchedulerName() {
        return "Summarizing";
    }

    @Override
    protected String getDistributedLockKey() {
        return SUMMARIZING_LOCK_KEY;
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
        Set<String> summarizingSttIds = redisTemplate.opsForSet()
                .members(STT_SUMMARIZING_SET);

        if (summarizingSttIds == null || summarizingSttIds.isEmpty()) {
            return;
        }

        log.info("Processing {} summary tasks", summarizingSttIds.size());

        List<Mono<Object>> tasks = summarizingSttIds.stream()
                .map(Long::valueOf)
                .map(jobExecutor::processSingleSummaryJob)
                .toList();

        Flux.merge(tasks)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("Some summary tasks failed", e);
                    return Mono.empty();
                })
                .blockLast();
    }
}
