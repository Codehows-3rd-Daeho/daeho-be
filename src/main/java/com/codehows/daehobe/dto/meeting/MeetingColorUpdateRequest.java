package com.codehows.daehobe.dto.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MeetingColorUpdateRequest {

    @NotBlank(message = "색상은 필수입니다.")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "올바른 HEX 색상 형식이 아닙니다. (#RRGGBB)")
    private String color;
}