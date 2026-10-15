package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.service.InternalBookingService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Booking", description = "Cho CareRecipient / Payment gọi nội bộ với header X-Internal-Secret; gateway chặn /internal/**.")
public class InternalBookingController {
    InternalBookingService internalBookingService;

    // caregiverUserId = user_id Identity của caregiver (sub trong JWT mà CareRecipientService nhận được).
    @GetMapping("/caregiver-access")
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam UUID caregiverUserId,
                                                                     @RequestParam UUID recipientId) {
        return internalBookingService.checkCaregiverAccess(caregiverUserId, recipientId);
    }

    @PostMapping("/{id}/deposit-paid")
    public APIResponse<BookingDetailResponse> markDepositPaid(@PathVariable UUID id,
                                                              @Valid @RequestBody(required = false) DepositPaidRequest request) {
        return internalBookingService.markDepositPaid(id, request);
    }

    @GetMapping("/occurrences/{occurrenceId}")
    public APIResponse<CaregiverOccurrenceResponse> getOccurrence(@PathVariable UUID occurrenceId) {
        return internalBookingService.getOccurrence(occurrenceId);
    }

    @GetMapping("/{id}")
    public APIResponse<BookingDetailResponse> get(@PathVariable UUID id) {
        return internalBookingService.get(id);
    }
}
