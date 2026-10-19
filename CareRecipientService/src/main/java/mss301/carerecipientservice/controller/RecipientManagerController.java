package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.RecipientManagerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-recipients/{recipientId}/managers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Recipient manager", description = "Người thân cùng quản lý hồ sơ. Xem: VIEWER; thêm/sửa/gỡ người khác, chuyển quản lý chính: OWNER; ai cũng tự rời được (trừ quản lý chính / OWNER cuối cùng).")
public class RecipientManagerController {
    RecipientManagerService recipientManagerService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<RecipientManagerResponse>> list(@CurrentUserId UUID actorId,
                                                            @PathVariable UUID recipientId) {
        return recipientManagerService.list(actorId, recipientId);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> create(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID recipientId,
                                                        @Valid @RequestBody ManagerCreateRequest request) {
        return recipientManagerService.create(actorId, recipientId, request);
    }

    @PatchMapping("/{managerId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> update(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID recipientId,
                                                        @PathVariable UUID managerId,
                                                        @Valid @RequestBody ManagerUpdateRequest request) {
        return recipientManagerService.update(actorId, recipientId, managerId, request);
    }

    @PatchMapping("/{managerId}/primary")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> assignPrimary(@CurrentUserId UUID actorId,
                                                               @PathVariable UUID recipientId,
                                                               @PathVariable UUID managerId) {
        return recipientManagerService.assignPrimary(actorId, recipientId, managerId);
    }

    @DeleteMapping("/{managerId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID recipientId,
                                    @PathVariable UUID managerId) {
        return recipientManagerService.delete(actorId, recipientId, managerId);
    }
}
