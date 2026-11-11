package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.DepositForfeitureResponse;
import mss301.paymentservice.dto.response.DepositForfeitureSummaryResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.DepositForfeitureService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deposit-forfeitures")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Deposit Forfeitures", description = "Tiền cọc khách bị giữ khi hủy sát giờ / quá hạn — caregiver của buổi hưởng, chưa có caregiver thì trung tâm hưởng")
public class DepositForfeitureController {

    DepositForfeitureService depositForfeitureService;

    // API danh sách tiền cọc bị giữ theo phạm vi người gọi.
    @GetMapping
    @PreAuthorize("hasAnyRole('CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<PageResponse<DepositForfeitureResponse>> list(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return depositForfeitureService.list(actorId, roles, claimCenterId, centerId, from, to, page, size);
    }

    // API tổng hợp tiền cọc bị giữ theo phạm vi người gọi.
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<DepositForfeitureSummaryResponse> getSummary(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID centerId) {
        return depositForfeitureService.getSummary(actorId, roles, claimCenterId, centerId, from, to);
    }
}
