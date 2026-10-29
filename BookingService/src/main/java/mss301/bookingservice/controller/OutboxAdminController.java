package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.OutboxSkipRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.OutboxAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-outbox")
@Tag(name = "Booking Outbox", description = "Admin xử lý việc gửi sang service khác bị FAILED (H-08)")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxAdminController {
    OutboxAdminService outboxAdminService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<OutboxEventResponse>> list(@RequestParam(required = false) OutboxStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return outboxAdminService.list(status, page, size);
    }

    @PostMapping("/{id}/requeue")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<OutboxEventResponse> requeue(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return outboxAdminService.requeue(actorId, id);
    }

    @PostMapping("/{id}/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<OutboxEventResponse> skip(@CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody OutboxSkipRequest request) {
        return outboxAdminService.skip(actorId, id, request.getNote());
    }
}
