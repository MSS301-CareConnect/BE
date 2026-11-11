package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CenterBookingPolicyRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CenterBookingPolicyResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CenterBookingPolicyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/center-booking-policies")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center booking policy", description = "Chính sách booking của trung tâm — đặt trước, hạn xác nhận / phân công / thanh toán, cọc, hoàn tiền")
public class CenterBookingPolicyController {

    CenterBookingPolicyService centerBookingPolicyService;

    // API xem chính sách booking của trung tâm.
    @GetMapping("/{centerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<CenterBookingPolicyResponse> get(@CurrentRoles Set<RoleCode> roles,
                                                        @CurrentCenterId(required = false) UUID scopeCenterId,
                                                        @PathVariable UUID centerId) {
        return centerBookingPolicyService.get(roles, scopeCenterId, centerId);
    }

    // API cập nhật chính sách booking của trung tâm.
    @PutMapping("/{centerId}")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER', 'ADMIN')")
    public APIResponse<CenterBookingPolicyResponse> update(@CurrentUserId UUID actorId,
                                                           @CurrentRoles Set<RoleCode> roles,
                                                           @CurrentCenterId(required = false) UUID scopeCenterId,
                                                           @PathVariable UUID centerId,
                                                           @Valid @RequestBody CenterBookingPolicyRequest request) {
        return centerBookingPolicyService.update(actorId, roles, scopeCenterId, centerId, request);
    }
}
