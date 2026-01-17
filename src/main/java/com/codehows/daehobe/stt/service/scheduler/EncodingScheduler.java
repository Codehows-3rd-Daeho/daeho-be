package com.codehows.daehobe.stt.service.scheduler;

import com.codehows.daehobe.stt.service.processor.DistributedLockManager;
import com.codehows.daehobe.stt.service.processor.SttJobProcessor;
import com.codehows.daehobe.stt.service.constant.SttRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.ENCODING_LOCK_KEY;

@Component
@Slf4j
public class EncodingScheduler extends AbstractSttScheduler {

    private final SttJobProcessor jobExecutor;

    public EncodingScheduler(
            RedisTemplate<String, String> redisTemplate,
            DistributedLockManager lockManager,
            SttJobProcessor jobExecutor) {
        super("STT-Encoding", redisTemplate, lockManager);
        this.jobExecutor = jobExecutor;
    }

    @Override
    protected String getSchedulerName() {
        return "Encoding";
    }

    @Override
    protected String getDistributedLockKey() {
        return ENCODING_LOCK_KEY;
    }

    @Override
    protected long getInitialDelay() {
        return 0;
    }

    @Override
    protected long getPeriod() {
        return 5;  // 5초 주기
    }

    @Override
    protected void processTask() {
        Set<String> encodingSttIds = redisTemplate.opsForSet()
                .members(SttRedisKeys.STT_ENCODING_SET);

        if (encodingSttIds == null || encodingSttIds.isEmpty()) {
            return;
        }

        log.info("Processing {} encoding tasks", encodingSttIds.size());

        List<Mono<Void>> tasks = encodingSttIds.stream()
                .map(Long::valueOf)
                .map(jobExecutor::processSingleEncodingJob)
                .toList();

        Flux.merge(tasks)
                .timeout(Duration.ofSeconds(120))
                .onErrorResume(e -> {
                    log.warn("Some encoding tasks failed", e);
                    return Mono.empty();
                })
                .blockLast();
    }
}