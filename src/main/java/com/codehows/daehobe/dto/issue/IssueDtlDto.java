package com.codehows.daehobe.dto.issue;

import com.codehows.daehobe.dto.file.FileDto;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDtlDto {
    // 왼쪽
    private String title;
    private String content;
    private List<FileDto> fileList;

    // 오른쪽
    private String status;
    private String host; // 이름, 직급 포함.
    private String startDate;
    private String endDate;
    private String categoryName;
    private List<String> departmentName;

    private String createdAt;
    private String updatedAt;

    private boolean isEditPermitted; // 요청자가 수정, 삭제 권한자인지
}
