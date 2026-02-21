package com.codehows.daehobe.logging.AOP.aspect;

import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.logging.AOP.interfaces.Auditable;
import com.codehows.daehobe.logging.AOP.annotations.AuditableField;
import com.codehows.daehobe.logging.AOP.interfaces.CommentLogInfoProvider;
import com.codehows.daehobe.logging.AOP.interfaces.Loggable;
import com.codehows.daehobe.logging.AOP.annotations.TrackChanges;
import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.logging.entity.Log;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.logging.repository.LogRepository;
import com.codehows.daehobe.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final MemberRepository memberRepository;
    private final LogRepository logRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    private record BeforeState(Auditable<?> entity, List<String> memberNames) {}

    @Pointcut("@annotation(com.codehows.daehobe.logging.AOP.annotations.TrackChanges)")
    public void trackChangesPointcut() {}

    @Around("trackChangesPointcut() && @annotation(trackChanges)")
    @Transactional
    public Object trackChanges(ProceedingJoinPoint joinPoint, TrackChanges trackChanges) throws Throwable {

        Object[] args = joinPoint.getArgs();
        Long entityId = (args.length > 0 && args[0] instanceof Long id) ? id : null;
        String currentMemberName = getCurrentMemberName();

        BeforeState beforeState = captureBeforeState(
                entityId,
                trackChanges,
                ((MethodSignature) joinPoint.getSignature()).getReturnType()
        );

        Object result = joinPoint.proceed();

        if (trackChanges.type() == ChangeType.CREATE) {
            if (result instanceof Auditable<?> created) {
                logCreateOrDelete(created, ChangeType.CREATE, trackChanges, currentMemberName);
            }
        } else if (trackChanges.type() == ChangeType.DELETE && beforeState.entity() != null) {
            logCreateOrDelete(beforeState.entity(), ChangeType.DELETE, trackChanges, currentMemberName);
        } else if (trackChanges.type() == ChangeType.UPDATE && beforeState.entity() != null) {
            Long id = (Long) beforeState.entity().getId();
            Object fresh = entityManager.find(beforeState.entity().getClass(), id);
            if (fresh instanceof Auditable<?> after) {
                logUpdate(beforeState, after, trackChanges, currentMemberName);
            }
        }

        return result;
    }

    private BeforeState captureBeforeState(Long entityId, TrackChanges trackChanges, Class<?> entityClass) {
        if (entityId == null) {
            return new BeforeState(null, Collections.emptyList());
        }

        Auditable<?> beforeEntity = null;
        List<String> beforeMemberNames = Collections.emptyList();

        if (trackChanges.type() == ChangeType.UPDATE || trackChanges.type() == ChangeType.DELETE) {
            try {
                Object found = entityManager.find(entityClass, entityId);
                if (found instanceof Auditable<?>) {
                    entityManager.detach(found);
                    beforeEntity = (Auditable<?>) found;
                }
            } catch (Exception e) {
                log.error("Snapshot creation failed for entityId: {} and class: {}", entityId, entityClass, e);
            }
        }

        if (trackChanges.trackMembers()) {
            beforeMemberNames = getMemberNames(trackChanges.target(), entityId);
        }

        return new BeforeState(beforeEntity, beforeMemberNames);
    }

    private void logCreateOrDelete(Auditable<?> entity, ChangeType type, TrackChanges trackChanges, String memberName) {
        if (!(entity instanceof Loggable)) return;
        try {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("type", type.name());
            String displayValue = entity.getTitle() != null ? entity.getTitle() : "";
            if (type == ChangeType.CREATE) {
                msgMap.put("after", displayValue);
            } else {
                msgMap.put("before", displayValue);
            }
            String message = objectMapper.writeValueAsString(msgMap);
            saveLog(entity, trackChanges, null, message, memberName);
        } catch (Exception e) {
            log.error("Failed to build log message for {} entityId={}", type, entity.getId(), e);
        }
    }

    private void logUpdate(BeforeState beforeState, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        logFieldChanges(beforeState.entity(), after, trackChanges, memberName);
        if (trackChanges.trackMembers()) {
            logMemberChanges(beforeState.memberNames(), after, trackChanges, memberName);
        }
    }

    private void logFieldChanges(Auditable<?> before, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        if (!(after instanceof Loggable)) return;

        for (Field field : after.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(AuditableField.class)) continue;

            try {
                field.setAccessible(true);
                Object beforeVal = field.get(before);
                Object afterVal = field.get(after);

                if (Objects.equals(beforeVal, afterVal)) continue;

                AuditableField meta = field.getAnnotation(AuditableField.class);

                Map<String, Object> msgMap = new LinkedHashMap<>();
                msgMap.put("type", "UPDATE");
                msgMap.put("field", meta.name());
                msgMap.put("before", toDisplayString(beforeVal));
                msgMap.put("after", toDisplayString(afterVal));
                String message = objectMapper.writeValueAsString(msgMap);

                saveLog(after, trackChanges, meta.name(), message, memberName);
            } catch (Exception e) {
                log.error("Error processing field for audit: {}", field.getName(), e);
            }
        }
    }

    private void logMemberChanges(List<String> beforeIds, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        if (!(after instanceof Loggable)) return;

        List<String> afterMemberNames = getMemberNames(trackChanges.target(), (Long) after.getId());
        Collections.sort(beforeIds);
        Collections.sort(afterMemberNames);

        if (!beforeIds.equals(afterMemberNames)) {
            try {
                String beforeNames = "[" + String.join(", ", beforeIds) + "]";
                String afterNames = "[" + String.join(", ", afterMemberNames) + "]";
                Map<String, Object> msgMap = new LinkedHashMap<>();
                msgMap.put("type", "UPDATE");
                msgMap.put("field", "참여자");
                msgMap.put("before", beforeNames);
                msgMap.put("after", afterNames);
                String message = objectMapper.writeValueAsString(msgMap);
                saveLog(after, trackChanges, "참여자", message, memberName);
            } catch (Exception e) {
                log.error("Failed to build member change log message", e);
            }
        }
    }

    private String toDisplayString(Object value) {
        if (value == null) return "";
        try {
            return (String) value.getClass().getMethod("getName").invoke(value);
        } catch (Exception ignored) {}
        return String.valueOf(value);
    }

    private List<String> getMemberNames(TargetType type, Long id) {
        String queryStr = switch (type) {
            case ISSUE -> "SELECT im.member.name FROM IssueMember im WHERE im.issue.id = :id";
            case MEETING -> "SELECT mm.member.name FROM MeetingMember mm WHERE mm.meeting.id = :id";
            default -> null;
        };

        if (queryStr == null) return Collections.emptyList();

        return entityManager.createQuery(queryStr, String.class)
                .setParameter("id", id)
                .getResultList();
    }

    private void saveLog(Auditable<?> after, TrackChanges trackChanges, String fieldName, String message, String memberName) {
        String targetTitle = after.getTitle();
        Long parentId = null;
        String parentType = null;

        if (trackChanges.target() == TargetType.COMMENT) {
            if (after instanceof CommentLogInfoProvider provider) {
                parentId = provider.getParentTargetId();
                parentType = provider.getParentTargetType().toString();
            } else {
                log.warn("Comment entity {} does not implement CommentLogInfoProvider. Cannot log parent info.", after.getId());
            }
        }

        logRepository.save(Log.builder()
                .targetId((Long) after.getId())
                .parentId(parentId)
                .parentType(parentType)
                .title(targetTitle)
                .targetType(trackChanges.target())
                .changeType(trackChanges.type())
                .updateField(fieldName)
                .message(message)
                .memberName(memberName)
                .build());
    }

    private String getCurrentMemberName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "SYSTEM";
        }
        return memberRepository.findById(Long.valueOf(auth.getName()))
                .map(Member::getName)
                .orElse("UNKNOWN");
    }
}
