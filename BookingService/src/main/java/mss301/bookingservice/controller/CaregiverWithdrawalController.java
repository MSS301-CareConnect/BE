package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CaregiverWithdrawalRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverWithdrawalResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CaregiverWithdrawalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregiver-withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver Withdrawal", description = "Caregiver xin hủy buổi được giao (trước giờ bắt đầu ≥ 24h); Center Manager phân người khác")
public class CaregiverWithdrawalController {

    CaregiverWithdrawalService caregiverWithdrawalService;

    // API danh sách lần caregiver rút khỏi buổi.
    @GetMapping
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverWithdrawalResponse>> list(@CurrentUserId UUID actorId,
                                                                       @CurrentRoles Set<RoleCode> roles,
                                                                       @CurrentCenterId(required = false) UUID centerId,
                                                                       @RequestParam(required = false) UUID bookingId,
                                                                       @RequestParam(defaultValue = "false") boolean pendingOnly,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return caregiverWithdrawalService.list(actorId, roles, centerId, bookingId, pendingOnly, page, size);
    }

    // API caregiver xin rút khỏi buổi / cả ngày.
    @PostMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<List<CaregiverWithdrawalResponse>> create(@CurrentUserId UUID actorId,
                                                                 @Valid @RequestBody CaregiverWithdrawalRequest request) {
        return caregiverWithdrawalService.create(actorId, request);
    }
}
