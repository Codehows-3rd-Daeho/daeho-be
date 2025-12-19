package com.codehows.daehobe.aop;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.repository.log.LogRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;


@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {
    private final LogRepository logRepository;

    @Pointcut("@annotation(com.codehows.daehobe.aop.TrackChanges)")
    public void trackChangesPointcut(){}


    @Around("trackChangesPointcut() && @annotation(trackChanges)")
    @Transactional
    public Object trackChanges(ProceedingJoinPoint joinPoint, TrackChanges trackChanges) throws Throwable {

        // create 처리
        if (trackChanges.type() != ChangeType.CREATE) {
            return joinPoint.proceed();
        }

        Object result = joinPoint.proceed();

        if (!(result instanceof Auditable<?> auditable)) {
            return result;
        }

        Long targetId = (Long) auditable.getId();

        String message = buildCreateMessage(result);

        Log log = Log.builder()
                .targetId(targetId)
                .targetType(trackChanges.target())   // ISSUE
                .changeType(ChangeType.CREATE)
                .message(message)
                .updateField(null)
                .build();

        logRepository.save(log);

        return result;
    }

    private String buildCreateMessage(Object entity) {
        String titleValue = null;

        for (Field field : entity.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(AuditableField.class)) {
                continue;
            }

            AuditableField meta = field.getAnnotation(AuditableField.class);

            if (!"제목".equals(meta.name())) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                if (value != null) {
                    titleValue = value.toString();
                }
            } catch (IllegalAccessException ignored) {}
        }

        return titleValue != null
                ? "등록 > " + titleValue
                : "등록";
    }

}
