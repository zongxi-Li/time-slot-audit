package com.timeslot.identity.dto;

import com.timeslot.identity.domain.BookingQualification;

public record QualificationResponse(Long userId, boolean eligible, String reason, Integer creditScore,
                                    String restrictedUntil) {
    public static QualificationResponse from(BookingQualification qualification) {
        return new QualificationResponse(qualification.userId(), qualification.eligible(), qualification.reason(),
                qualification.creditScore(), qualification.formattedRestrictedUntil());
    }
}
