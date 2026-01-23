package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.masterData.entity.SetNotification;
import com.codehows.daehobe.masterData.repository.SetNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetNotificationServiceTest {

    @Mock
    private SetNotificationRepository setNotificationRepository;

    @InjectMocks
    private SetNotificationService setNotificationService;

    @Test
    @DisplayName("성공: 알림 설정 저장")
    void saveSetting_Success() {
        // given
        SetNotificationDto dto = new SetNotificationDto(false, true, false, true, false);

        // when
        setNotificationService.saveSetting(dto);

        // then
        verify(setNotificationRepository).save(any(SetNotification.class));
    }

    @Test
    @DisplayName("성공: 알림 설정 조회 (기존 설정 있음)")
    void getSetting_Existing() {
        // given
        SetNotification existingSetting = SetNotification.builder()
                .id(1L)
                .issueCreated(false)
                .issueStatus(false)
                .build();
        when(setNotificationRepository.findById(1L)).thenReturn(Optional.of(existingSetting));

        // when
        SetNotificationDto result = setNotificationService.getSetting();

        // then
        assertThat(result.isIssueCreated()).isFalse();
        assertThat(result.isIssueStatus()).isFalse();
    }

    @Test
    @DisplayName("성공: 알림 설정 조회 (기존 설정 없음, 기본값 생성)")
    void getSetting_NotExisting_CreatesDefault() {
        // given
        SetNotification defaultSetting = SetNotification.builder()
                .id(1L)
                .issueCreated(true)
                .issueStatus(true)
                .meetingCreated(true)
                .meetingStatus(true)
                .commentMention(true)
                .build();

        when(setNotificationRepository.findById(1L)).thenReturn(Optional.empty());
        when(setNotificationRepository.save(any(SetNotification.class))).thenReturn(defaultSetting);

        // when
        SetNotificationDto result = setNotificationService.getSetting();

        // then
        verify(setNotificationRepository).save(any(SetNotification.class));
        assertThat(result.isIssueCreated()).isTrue();
        assertThat(result.isMeetingCreated()).isTrue();
        assertThat(result.isCommentMention()).isTrue();
    }
}
