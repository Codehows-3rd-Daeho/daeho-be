package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.masterData.entity.SetNotification;
import com.codehows.daehobe.masterData.repository.SetNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SetNotificationService {
    private final SetNotificationRepository setNotificationRepository;

    public void saveSetting(SetNotificationDto dto) {
        SetNotification setting = SetNotification.builder()
                .id(1L)
                .issueCreated(dto.isIssueCreated())
                .issueStatus(dto.isIssueStatus())
                .meetingCreated(dto.isMeetingCreated())
                .meetingStatus(dto.isMeetingStatus())
                .commentMention(dto.isCommentMention())
                .build();
        setNotificationRepository.save(setting);
    }

    // 조회
    public SetNotificationDto getSetting() {
        SetNotification entity = setNotificationRepository.findById(1L)
                .orElseGet(() -> {
                    // DB에 없으면 기본 값으로 새 엔티티 생성 후 저장
                    SetNotification newEntity = SetNotification.builder()
                            .id(1L)
                            .issueCreated(true)
                            .issueStatus(true)
                            .meetingCreated(true)
                            .meetingStatus(true)
                            .commentMention(true)
                            .build();
                    return setNotificationRepository.save(newEntity);
                });
        return SetNotificationDto.fromEntity(entity);
    }

}