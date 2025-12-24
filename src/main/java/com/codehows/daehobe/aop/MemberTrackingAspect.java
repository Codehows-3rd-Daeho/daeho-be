package com.codehows.daehobe.aop;

import com.codehows.daehobe.constant.ChangeType;
import com.codehows.daehobe.constant.TargetType;
import com.codehows.daehobe.entity.log.Log;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.repository.log.LogRepository;
import com.codehows.daehobe.repository.member.MemberRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
public class MemberTrackingAspect {
    private final LogRepository logRepository;
    private final MemberRepository memberRepository;
    private final EntityManager entityManager;

    @Around("@annotation(trackMemberChanges)")
    @Transactional // 트랜잭션 보장
    public Object track(ProceedingJoinPoint joinPoint, TrackMemberChanges trackMemberChanges) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Long id)) {
            return joinPoint.proceed();
        }

        TargetType type = trackMemberChanges.target();

        // 1. 수정 전 참여자 ID 추출
        List<Long> beforeIds = getMemberIds(type, id);

        // 2. 실제 비즈니스 로직 실행
        Object result = joinPoint.proceed();

        // 3. 수정 후 참여자 ID 추출
        List<Long> afterIds = getMemberIds(type, id);

        // 4. 리스트 비교 (순서 무관하게 내용물 비교)
        Collections.sort(beforeIds);
        Collections.sort(afterIds);

        if (!beforeIds.equals(afterIds)) {
            saveMemberLog(id, type, result);
        }

        return result;
    }

    // 타겟 타입에 따른 동적 쿼리 처리
    private List<Long> getMemberIds(TargetType type, Long id) {
        String queryStr = switch (type) {
            case ISSUE -> "SELECT im.member.id FROM IssueMember im WHERE im.issue.id = :id";
            case MEETING -> "SELECT mm.member.id FROM MeetingMember mm WHERE mm.meeting.id = :id";
            default -> null;
        };

        if (queryStr == null) return Collections.emptyList();

        return entityManager.createQuery(queryStr, Long.class)
                .setParameter("id", id)
                .getResultList();
    }

    private void saveMemberLog(Long targetId, TargetType targetType, Object entity) {
        if (entity instanceof Loggable loggable) {
            String message = loggable.createLogMessage(ChangeType.UPDATE, "참여자");

            logRepository.save(
                    Log.builder()
                            .targetId(targetId)
                            .targetType(targetType)
                            .changeType(ChangeType.UPDATE)
                            .updateField("참여자")
                            .message(message)
                            .memberName(getCurrentMemberName())
                            .build()
            );
        }
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