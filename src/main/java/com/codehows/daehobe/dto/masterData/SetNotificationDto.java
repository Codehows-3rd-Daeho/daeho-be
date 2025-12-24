package com.codehows.daehobe.dto.masterData;

import com.codehows.daehobe.entity.notification.SetNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetNotificationDto {
    private boolean issueCreated;
    private boolean issueStatus;
    private boolean meetingCreated;
    private boolean meetingStatus;
    private boolean commentMention;

    public static SetNotificationDto fromEntity(SetNotification entity) {
        return SetNotificationDto.builder()
                .issueCreated(entity.isIssueCreated())
                .issueStatus(entity.isIssueStatus())
                .meetingCreated(entity.isMeetingCreated())
                .meetingStatus(entity.isMeetingStatus())
                .commentMention(entity.isCommentMention())
                .build();
    }
}
