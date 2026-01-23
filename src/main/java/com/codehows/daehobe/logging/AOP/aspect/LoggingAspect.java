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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final MemberRepository memberRepository;
    private final LogRepository logRepository;
    private final EntityManager entityManager;

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
                ((MethodSignature)joinPoint.getSignature()).getReturnType()
        );

        Object result = joinPoint.proceed();

        if (!(result instanceof Auditable<?> after)) {
            return result;
        }

        if (trackChanges.type() == ChangeType.CREATE || trackChanges.type() == ChangeType.DELETE) {
            logCreateOrDelete(after, trackChanges, currentMemberName);
        } else if (trackChanges.type() == ChangeType.UPDATE && beforeState.entity() != null) {
            logUpdate(beforeState, after, trackChanges, currentMemberName);
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

    private void logCreateOrDelete(Auditable<?> after, TrackChanges trackChanges, String memberName) {
        if (after instanceof Loggable loggable) {
            String message = loggable.createLogMessage(trackChanges.type());
            if (message != null) {
                saveLog(after, trackChanges, null, message, memberName);
            }
        }
    }

    private void logUpdate(BeforeState beforeState, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        logFieldChanges(beforeState.entity(), after, trackChanges, memberName);
        if (trackChanges.trackMembers()) {
            logMemberChanges(beforeState.memberNames(), after, trackChanges, memberName);
        }
    }

    private void logFieldChanges(Auditable<?> before, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        if (!(after instanceof Loggable loggable)) return;

        for (Field field : after.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(AuditableField.class)) continue;

            try {
                field.setAccessible(true);
                Object beforeVal = field.get(before);
                Object afterVal = field.get(after);

                if (Objects.equals(beforeVal, afterVal)) continue;

                AuditableField meta = field.getAnnotation(AuditableField.class);

                String message = loggable.createLogMessage(ChangeType.UPDATE, meta.name());
                if (message != null) {
                    saveLog(after, trackChanges, meta.name(), message, memberName);
                }
            } catch (IllegalAccessException e) {
                log.error("Error accessing field for audit: {}", field.getName(), e);
            }
        }
    }

    private void logMemberChanges(List<String> beforeIds, Auditable<?> after, TrackChanges trackChanges, String memberName) {
        if (!(after instanceof Loggable)) return;

        List<String> afterMemberNames = getMemberNames(trackChanges.target(), (Long) after.getId());
        Collections.sort(beforeIds);
        Collections.sort(afterMemberNames);

        if (!beforeIds.equals(afterMemberNames)) {
            String names = String.join(", ", afterMemberNames);
            String message = "참여자 > [" + names + "]";
            saveLog(after, trackChanges, "참여자", message, memberName);
        }
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