package com.codehows.daehobe.aop;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.entity.issue.IssueMember;
import com.codehows.daehobe.entity.log.Auditable;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.log.LogRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {
    private final MemberRepository memberRepository;
    private final LogRepository logRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Pointcut("@annotation(com.codehows.daehobe.aop.TrackChanges)")
    public void trackChangesPointcut() {}

    @Around("trackChangesPointcut() && @annotation(trackChanges)")
    @Transactional
    public Object trackChanges(ProceedingJoinPoint joinPoint, TrackChanges trackChanges) throws Throwable {

        // 1. 현재 사용자 이름 가져오기
        String currentMemberName = getCurrentMemberName();



        Auditable<?> before = null;

        // 2. UPDATE면 BEFORE 스냅샷 생성
        if (trackChanges.type() == ChangeType.UPDATE || trackChanges.type() == ChangeType.DELETE) {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof Long id) {
                Class<?> entityClass = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();

                try {
                    Object found = entityManager.find(entityClass, id);
                    if (found instanceof Auditable<?>) {
                        entityManager.detach(found);
                        before = (Auditable<?>) found;
                    }
                } catch (Exception e) {
                    System.out.println("Snapshot failed: " + e.getMessage());
                }
            }
        }

        // 3. 비즈니스 로직 실행
        Object result = joinPoint.proceed();

        if (!(result instanceof Auditable<?> after)) {
            return result;
        }

        // 4. CREATE / DELETE 처리
        if (trackChanges.type() == ChangeType.CREATE || trackChanges.type() == ChangeType.DELETE) {
            if (after instanceof Loggable loggable) {
                String message = loggable.createLogMessage(trackChanges.type());
                if (message != null) {
                    saveLog(after, trackChanges, null, message, currentMemberName);
                }
            }
            return result;
        }

        // 5. UPDATE 처리 (일반 필드만)
        if (trackChanges.type() == ChangeType.UPDATE && before != null) {
            if (after instanceof Loggable loggable) {
                for (Field field : after.getClass().getDeclaredFields()) {
                    if (!field.isAnnotationPresent(AuditableField.class)) continue;

                    // ⭐ 리스트(참여자/부서 등)는 MemberTrackingAspect에서 처리하므로 제외
                    if (field.getType().equals(List.class)) continue;

                    field.setAccessible(true);
                    Object beforeVal = field.get(before);
                    Object afterVal = field.get(after);

                    if (Objects.equals(beforeVal, afterVal)) continue;

                    AuditableField meta = field.getAnnotation(AuditableField.class);
                    String message = loggable.createLogMessage(ChangeType.UPDATE, meta.name());

                    if (message != null) {
                        saveLog(after, trackChanges, meta.name(), message, currentMemberName);
                    }
                }
            }
        }

        return result; // 👈 반드시 비즈니스 로직 결과값을 반환해야 합니다.
    }

    // 로그 저장 공통 로직
    private void saveLog(Auditable<?> after, TrackChanges trackChanges, String fieldName, String message, String memberName) {
        String targetTitle = after.getTitle();
        Long parentId = null;
        String parentType = null;

        if (trackChanges.target() == com.codehows.daehobe.constant.TargetType.COMMENT) {
            try {
                // 부모 ID 추출 (이미 작성하신 코드)
                java.lang.reflect.Method getTargetIdMethod = after.getClass().getMethod("getTargetId");
                parentId = (Long) getTargetIdMethod.invoke(after);
                java.lang.reflect.Method getTargetTypeMethod = after.getClass().getMethod("getTargetType");
                parentType = getTargetTypeMethod.invoke(after).toString();
            } catch (Exception e) {
                System.out.println("부모 정보 추출 실패: " + e.getMessage());
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

    // 사용자 이름 조회 로직
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