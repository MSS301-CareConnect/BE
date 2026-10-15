package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.service.AdminBookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin - Booking", description = "ADMIN xem booking toàn hệ thống (chỉ đọc).")
public class AdminBookingController {
    AdminBookingService adminBookingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<BookingSummaryResponse>> list(@RequestParam(required = false) BookingStatus status,
                                                                  @RequestParam(required = false) UUID centerId,
                                                                  @RequestParam(required = false) UUID customerId,
                                                                  @RequestParam(required = false) UUID recipientId,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return adminBookingService.list(status, centerId, customerId, recipientId, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<BookingDetailResponse> get(@PathVariable UUID id) {
        return adminBookingService.get(id);
    }
}
