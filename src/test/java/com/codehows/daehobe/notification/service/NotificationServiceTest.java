package com.codehows.daehobe.notification.service;

import com.codehows.daehobe.common.PerformanceLoggingExtension;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.member.service.MemberService;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.NotificationResponseDto;
import com.codehows.daehobe.notification.entity.Notification;
import com.codehows.daehobe.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PerformanceLoggingExtension.class})
class NotificationServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MemberService memberService;
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("성공: 알림 저장")
    void saveNotification_Success() {
        // given
        Long memberId = 1L;
        NotificationMessageDto messageDto = new NotificationMessageDto("테스트 메시지", "/url");
        Member member = Member.builder().id(memberId).build();
        when(memberService.getMemberById(memberId)).thenReturn(member);

        // when
        notificationService.saveNotification(memberId, messageDto);

        // then
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("성공: 알림 목록 조회")
    void getMyNotifications_Success() {
        // given
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Member member = Member.builder().id(memberId).build();
        Member sender = Member.builder().id(2L).name("보낸사람").build();
        Notification notification = Notification.builder()
                .id(1L)
                .member(member)
                .message("알림")
                .isRead(false)
                .build();
        ReflectionTestUtils.setField(notification, "createdBy", sender.getId());
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());
        Page<Notification> notificationPage = new PageImpl<>(Collections.singletonList(notification), pageable, 1);

        when(notificationRepository.findByMemberId(eq(memberId), any(Pageable.class))).thenReturn(notificationPage);
        when(memberService.getMemberById(sender.getId())).thenReturn(sender);

        // when
        Page<NotificationResponseDto> result = notificationService.getMyNotifications(memberId, 0, 10);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSenderName()).isEqualTo("보낸사람");
    }

    @Test
    @DisplayName("성공: 알림 읽음 처리")
    void readNotification_Success() {
        // given
        Long notificationId = 1L;
        Notification notification = spy(Notification.builder().id(notificationId).build());
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        
        // when
        notificationService.readNotification(notificationId);
        
        // then
        verify(notification).setIsRead();
    }
    
    @Test
    @DisplayName("실패: 알림 읽음 처리 (알림 없음)")
    void readNotification_NotFound() {
        // given
        Long notificationId = 99L;
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
        
        // when & then
        assertThrows(EntityNotFoundException.class, () -> notificationService.readNotification(notificationId));
    }

    @Test
    @DisplayName("성공: 읽지 않은 알림 수 조회")
    void getUnreadCount_Success() {
        // given
        Long memberId = 1L;
        when(notificationRepository.countByMemberIdAndIsReadFalse(memberId)).thenReturn(5);

        // when
        int count = notificationService.getUnreadCount(memberId);
        
        // then
        assertThat(count).isEqualTo(5);
    }
}
