/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.identity.dto;

import com.timeslot.identity.domain.BookingQualification;

public record QualificationResponse(Long userId, boolean eligible, String reason, Integer creditScore,
                                    String restrictedUntil) {
    public static QualificationResponse from(BookingQualification qualification) {
        return new QualificationResponse(qualification.userId(), qualification.eligible(), qualification.reason(),
                qualification.creditScore(), qualification.formattedRestrictedUntil());
    }
}
