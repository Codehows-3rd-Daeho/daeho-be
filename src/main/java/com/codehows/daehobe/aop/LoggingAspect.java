package com.codehows.daehobe.aop;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.repository.log.LogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final LogRepository logRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Pointcut("@annotation(com.codehows.daehobe.aop.TrackChanges)")
    public void trackChangesPointcut() {
    }

    @Around("trackChangesPointcut() && @annotation(trackChanges)")
    @Transactional
    public Object trackChanges(
            ProceedingJoinPoint joinPoint,
            TrackChanges trackChanges
    ) throws Throwable {

        Auditable<?> before = null;

        // 🔹 UPDATE면 BEFORE 먼저 조회
        if (trackChanges.type() == ChangeType.UPDATE) {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof Long id) {

                // ⚠️ 실제 엔티티 클래스는 service 반환 타입 기준
                Class<?> entityClass =
                        ((org.aspectj.lang.reflect.MethodSignature)
                                joinPoint.getSignature())
                                .getReturnType();

                Object found = entityManager.find(entityClass, id);

                if (found instanceof Auditable<?>) {
                    // 🔥 스냅샷용 분리
                    entityManager.detach(found);
                    before = (Auditable<?>) found;
                }
            }
        }

        // 🔹 실제 비즈니스 로직 실행
        Object result = joinPoint.proceed();

        if (!(result instanceof Auditable<?> after)) {
            return result;
        }

        // =========================
        // CREATE / DELETE
        // =========================
        if (trackChanges.type() == ChangeType.CREATE
                || trackChanges.type() == ChangeType.DELETE) {

            if (after instanceof Loggable loggable) {
                String message = loggable.createLogMessage(trackChanges.type());
                if (message == null) return result;

                logRepository.save(
                        Log.builder()
                                .targetId((Long) after.getId())
                                .targetType(trackChanges.target())
                                .changeType(trackChanges.type())
                                .message(message)
                                .build()
                );
            }
            return result;
        }

        // =========================
        // UPDATE (변경 필드만)
        // =========================
        if (trackChanges.type() == ChangeType.UPDATE && before != null) {

            if (!(after instanceof Loggable loggable)) return result;

            for (Field field : after.getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(AuditableField.class)) continue;

                field.setAccessible(true);

                Object beforeVal = field.get(before);
                Object afterVal = field.get(after);

                if (Objects.equals(beforeVal, afterVal)) continue;

                AuditableField meta = field.getAnnotation(AuditableField.class);

                String message = loggable.createLogMessage(
                        ChangeType.UPDATE,
                        meta.name()
                );

                if (message == null) continue;

                logRepository.save(
                        Log.builder()
                                .targetId((Long) after.getId())
                                .targetType(trackChanges.target())
                                .changeType(ChangeType.UPDATE)
                                .updateField(meta.name())
                                .message(message)
                                .build()
                );
            }
        }

        return result;
    }
}
