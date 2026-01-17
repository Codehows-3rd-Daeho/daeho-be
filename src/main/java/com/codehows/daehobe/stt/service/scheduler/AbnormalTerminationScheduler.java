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

import static com.codehows.daehobe.stt.service.constant.SttRedisKeys.ABNORMAL_TERMINATION_LOCK_KEY;

@Component
@Slf4j
public class AbnormalTerminationScheduler extends AbstractSttScheduler {

    private final SttJobProcessor jobExecutor;

    public AbnormalTerminationScheduler(
            RedisTemplate<String, String> redisTemplate,
            DistributedLockManager lockManager,
            SttJobProcessor jobExecutor) {
        super("STT-AbnormalTermination", redisTemplate, lockManager);
        this.jobExecutor = jobExecutor;
    }

    @Override
    protected String getSchedulerName() {
        return "AbnormalTermination";
    }

    @Override
    protected String getDistributedLockKey() {
        return ABNORMAL_TERMINATION_LOCK_KEY;
    }

    @Override
    protected long getInitialDelay() {
        return 0;
    }

    @Override
    protected long getPeriod() {
        return 30;
    }

    @Override
    protected void processTask() {
        Set<String> recordingSttIds = redisTemplate.opsForSet()
                .members(SttRedisKeys.STT_RECORDING_SET);

        if (recordingSttIds == null || recordingSttIds.isEmpty()) {
            return;
        }

        log.info("Processing {} encoding tasks", recordingSttIds.size());

        List<Mono<Void>> tasks = recordingSttIds.stream()
                .map(Long::valueOf)
                .map(jobExecutor::handleAbnormalTermination)
                .toList();

        Flux.merge(tasks)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("Some abnormal termination checks failed", e);
                    return Mono.empty();
                })
                .blockLast();
    }
}
