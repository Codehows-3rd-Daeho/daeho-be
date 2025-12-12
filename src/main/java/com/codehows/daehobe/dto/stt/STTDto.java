package com.codehows.daehobe.dto.stt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class STTDto {
private Long id;
private String content;
private String summary;
private String meetingId;

}
