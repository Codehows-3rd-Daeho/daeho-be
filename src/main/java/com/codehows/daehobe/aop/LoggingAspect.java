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

/**
 * 엔티티의 생성, 수정, 삭제 이력을 추적하고 로그를 기록하는 AOP Aspect 클래스입니다.
 * `@TrackChanges` 어노테이션이 붙은 메서드를 대상으로 동작합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {
    private final MemberRepository memberRepository;
    private final LogRepository logRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * `@TrackChanges` 어노테이션이 적용된 모든 메서드를 대상으로 하는 Pointcut을 정의합니다.
     */
    @Pointcut("@annotation(com.codehows.daehobe.aop.TrackChanges)")
    public void trackChangesPointcut() {}

    /**
     * Pointcut에 해당하는 메서드 실행 전후로 변경 사항을 감지하고 로그를 기록합니다.
     *
     * @param joinPoint    AOP 프록시 대상 메서드
     * @param trackChanges 대상 메서드에 적용된 `@TrackChanges` 어노테이션
     * @return 대상 메서드의 실행 결과
     * @throws Throwable 대상 메서드 실행 중 발생할 수 있는 예외
     */
    @Around("trackChangesPointcut() && @annotation(trackChanges)")
    @Transactional
    public Object trackChanges(ProceedingJoinPoint joinPoint, TrackChanges trackChanges) throws Throwable {

        String currentMemberName = getCurrentMemberName();
        Auditable<?> before = null;

        // UPDATE 또는 DELETE의 경우, 메서드 실행 전 엔티티의 스냅샷을 생성합니다.
        if (trackChanges.type() == ChangeType.UPDATE || trackChanges.type() == ChangeType.DELETE) {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof Long id) {
                // 메서드의 반환 타입을 통해 엔티티 클래스를 추론합니다.
                Class<?> entityClass = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();
                try {
                    Object found = entityManager.find(entityClass, id);
                    if (found instanceof Auditable<?>) {
                        entityManager.detach(found); // 영속성 컨텍스트에서 분리하여 스냅샷으로 사용
                        before = (Auditable<?>) found;
                    }
                } catch (Exception e) {
                    System.out.println("Snapshot creation failed: " + e.getMessage());
                }
            }
        }

        // 대상 비즈니스 로직(메서드) 실행
        Object result = joinPoint.proceed();

        if (!(result instanceof Auditable<?> after)) {
            return result;
        }

        // CREATE 또는 DELETE 로그 처리
        if (trackChanges.type() == ChangeType.CREATE || trackChanges.type() == ChangeType.DELETE) {
            if (after instanceof Loggable loggable) {
                String message = loggable.createLogMessage(trackChanges.type());
                if (message != null) {
                    saveLog(after, trackChanges, null, message, currentMemberName);
                }
            }
            return result;
        }

        // UPDATE 로그 처리
        if (trackChanges.type() == ChangeType.UPDATE && before != null) {
            if (after instanceof Loggable loggable) {
                // `@AuditableField`가 붙은 필드들의 변경 사항을 비교
                for (Field field : after.getClass().getDeclaredFields()) {
                    if (!field.isAnnotationPresent(AuditableField.class)) continue;
                    if (List.class.isAssignableFrom(field.getType())) continue; // 리스트 필드는 MemberTrackingAspect에서 별도 처리

                    field.setAccessible(true);
                    Object beforeVal = field.get(before);
                    Object afterVal = field.get(after);

                    if (Objects.equals(beforeVal, afterVal)) continue; // 변경되지 않았으면 건너뜀

                    AuditableField meta = field.getAnnotation(AuditableField.class);
                    String message = loggable.createLogMessage(ChangeType.UPDATE, meta.name());
                    if (message != null) {
                        saveLog(after, trackChanges, meta.name(), message, currentMemberName);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 생성된 로그 메시지를 데이터베이스에 저장합니다.
     *
     * @param after        변경 후의 Auditable 엔티티
     * @param trackChanges `@TrackChanges` 어노테이션 정보
     * @param fieldName    변경된 필드의 이름 (UPDATE 시에만 사용)
     * @param message      기록할 로그 메시지
     * @param memberName   작업을 수행한 사용자의 이름
     */
    private void saveLog(Auditable<?> after, TrackChanges trackChanges, String fieldName, String message, String memberName) {
        String targetTitle = after.getTitle();
        Long parentId = null;
        String parentType = null;

        // 댓글의 경우 부모 엔티티(이슈/회의) 정보를 추출하여 함께 기록합니다.
        if (trackChanges.target() == com.codehows.daehobe.constant.TargetType.COMMENT) {
            try {
                java.lang.reflect.Method getTargetIdMethod = after.getClass().getMethod("getTargetId");
                parentId = (Long) getTargetIdMethod.invoke(after);
                java.lang.reflect.Method getTargetTypeMethod = after.getClass().getMethod("getTargetType");
                parentType = getTargetTypeMethod.invoke(after).toString();
            } catch (Exception e) {
                System.out.println("Failed to extract parent entity info for comment log: " + e.getMessage());
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

    /**
     * Spring Security Context에서 현재 인증된 사용자의 이름을 조회합니다.
     *
     * @return 인증된 사용자 이름, 없으면 "SYSTEM" 또는 "UNKNOWN"
     */
    private String getCurrentMemberName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "SYSTEM"; // 인증 정보가 없는 경우 시스템 작업으로 간주
        }
        return memberRepository.findById(Long.valueOf(auth.getName()))
                .map(Member::getName)
                .orElse("UNKNOWN"); // DB에 해당 사용자가 없는 비정상적인 경우
    }
}