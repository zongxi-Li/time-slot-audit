/**
 * 文件职责：向前端返回用户预约资格、信用分和限制状态。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将领域资格结果转换为 API 响应。
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
