package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.OutboxSkipRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ProviderOutboxService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provider-outbox")
@Tag(name = "Provider Outbox", description = "Admin xử lý sự kiện cascade trung tâm bị FAILED (H-08)")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProviderOutboxController {
    ProviderOutboxService providerOutboxService;

    // API admin xem danh sách sự kiện outbox.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<OutboxEventResponse>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return providerOutboxService.list(status, page, size);
    }

    // API admin xếp lại sự kiện outbox đã FAILED.
    @PostMapping("/{id}/requeue")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<OutboxEventResponse> requeue(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return providerOutboxService.requeue(actorId, id);
    }

    // API admin bỏ qua sự kiện outbox đã FAILED.
    @PostMapping("/{id}/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<OutboxEventResponse> skip(@CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody OutboxSkipRequest request) {
        return providerOutboxService.skip(actorId, id, request.getNote());
    }
}
