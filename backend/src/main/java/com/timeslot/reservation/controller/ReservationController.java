package com.timeslot.reservation.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.reservation.dto.CancelReservationRequest;
import com.timeslot.reservation.dto.CreateReservationRequest;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/calendar")
    public ApiResponse<List<ReservationResponse>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Long roomId) {
        return ApiResponse.success(reservationService.calendar(start, end, roomId));
    }

    @GetMapping("/my")
    public ApiResponse<List<ReservationResponse>> mine() {
        return ApiResponse.success(reservationService.mine());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> create(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reservationService.createReservation(request)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ReservationResponse> cancel(@PathVariable Long id,
                                                   @Valid @RequestBody(required = false) CancelReservationRequest request) {
        return ApiResponse.success(reservationService.cancel(id, request));
    }
}
