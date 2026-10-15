package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking", description = "Đặt lịch chăm sóc của khách hàng. Booking không phải của mình → 404. Header Idempotency-Key chống tạo trùng.")
public class BookingController {
    BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> create(@CurrentUserId UUID actorId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(actorId, idempotencyKey, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> list(@CurrentUserId UUID actorId,
                                                                  @RequestParam(required = false) BookingStatus status,
                                                                  @RequestParam(required = false) UUID recipientId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return bookingService.list(actorId, status, recipientId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.get(actorId, id);
    }

    @GetMapping("/{id}/occurrences")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<OccurrenceResponse>> listOccurrences(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.listOccurrences(actorId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> cancel(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancel(actorId, id, request);
    }
}
