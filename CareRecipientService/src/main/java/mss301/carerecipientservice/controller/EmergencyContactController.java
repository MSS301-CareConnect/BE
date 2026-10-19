package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.EmergencyContactService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-recipients/{recipientId}/emergency-contacts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Emergency contact", description = "Liên hệ khẩn cấp của người bệnh (tối đa một liên hệ chính). Xem: VIEWER; ghi: EDITOR trở lên.")
public class EmergencyContactController {
    EmergencyContactService emergencyContactService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> create(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID recipientId,
                                                        @Valid @RequestBody EmergencyContactRequest request) {
        return emergencyContactService.create(actorId, recipientId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<EmergencyContactResponse>> list(@CurrentUserId UUID actorId,
                                                            @PathVariable UUID recipientId) {
        return emergencyContactService.list(actorId, recipientId);
    }

    @GetMapping("/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> get(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID recipientId,
                                                     @PathVariable UUID contactId) {
        return emergencyContactService.get(actorId, recipientId, contactId);
    }

    @PutMapping("/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> update(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID recipientId,
                                                        @PathVariable UUID contactId,
                                                        @Valid @RequestBody EmergencyContactRequest request) {
        return emergencyContactService.update(actorId, recipientId, contactId, request);
    }

    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID recipientId,
                                    @PathVariable UUID contactId) {
        return emergencyContactService.delete(actorId, recipientId, contactId);
    }

    @PatchMapping("/{contactId}/primary")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> assignPrimary(@CurrentUserId UUID actorId,
                                                               @PathVariable UUID recipientId,
                                                               @PathVariable UUID contactId) {
        return emergencyContactService.assignPrimary(actorId, recipientId, contactId);
    }
}
