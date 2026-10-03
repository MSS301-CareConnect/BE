package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.MedicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/care-recipients/{recipientId}/medications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Medication", description = "Thuốc của người bệnh. Xem: VIEWER; thêm/sửa/xóa/bật-tắt: EDITOR trở lên.")
public class MedicationController {
    MedicationService medicationService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> create(@CurrentUserId UUID actorId,
                                                  @PathVariable UUID recipientId,
                                                  @Valid @RequestBody MedicationRequest request) {
        return medicationService.create(actorId, recipientId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<MedicationResponse>> list(@CurrentUserId UUID actorId,
                                                      @PathVariable UUID recipientId,
                                                      @RequestParam(required = false) Boolean active) {
        return medicationService.list(actorId, recipientId, active);
    }

    @GetMapping("/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> get(@CurrentUserId UUID actorId,
                                               @PathVariable UUID recipientId,
                                               @PathVariable UUID medicationId) {
        return medicationService.get(actorId, recipientId, medicationId);
    }

    @PutMapping("/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> update(@CurrentUserId UUID actorId,
                                                  @PathVariable UUID recipientId,
                                                  @PathVariable UUID medicationId,
                                                  @Valid @RequestBody MedicationRequest request) {
        return medicationService.update(actorId, recipientId, medicationId, request);
    }

    @DeleteMapping("/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID recipientId,
                                    @PathVariable UUID medicationId) {
        return medicationService.delete(actorId, recipientId, medicationId);
    }

    @PatchMapping("/{medicationId}/active")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> updateActive(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID recipientId,
                                                        @PathVariable UUID medicationId,
                                                        @RequestParam boolean active) {
        return medicationService.updateActive(actorId, recipientId, medicationId, active);
    }
}
