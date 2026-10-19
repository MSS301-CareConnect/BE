package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-occurrences")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking Occurrence", description = "Quản lý ca/buổi chăm sóc (Caregiver, Admin, Inter-service)")
public class BookingOccurrenceController {

    BookingOccurrenceService bookingOccurrenceService;

    @GetMapping
    @Operation(summary = "Lịch chăm sóc được giao cho caregiver đang đăng nhập")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<List<CaregiverOccurrenceResponse>> list(@CurrentUserId UUID actorId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) OccurrenceStatus status) {
        return bookingOccurrenceService.list(actorId, from, to, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một ca/buổi chăm sóc")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverOccurrenceResponse> get(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID id,
                                                        Authentication authentication) {
        boolean isCaregiverOnly = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CAREGIVER"))
                && authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_CENTER_MANAGER"));
        if (isCaregiverOnly) {
            return bookingOccurrenceService.get(actorId, id);
        }
        return bookingOccurrenceService.getById(id);
    }
}
