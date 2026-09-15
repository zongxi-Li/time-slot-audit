package com.timeslot.administration.spi;

/**
 * Administration-side port for reservation-owned state changes.
 * An adapter must delegate to the reservation domain's public lifecycle service.
 */
public interface ReservationLifecyclePort {
    void approve(Long reservationId, Long operatorId);

    void reject(Long reservationId, Long operatorId, String reason);

    void forceCancel(Long reservationId, Long operatorId, String reason);
}
