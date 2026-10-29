package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.ComplaintRefundRequest;
import mss301.paymentservice.dto.request.RefundCreateRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.RefundCreateResponse;
import mss301.paymentservice.dto.response.RefundResponse;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.RefundService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Refunds", description = "Lệnh hoàn tiền (BookingService gửi qua outbox), lịch sử hoàn và thử lại khi lỗi")
public class RefundController {
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    RefundService refundService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CUSTOMER')")
    public APIResponse<PageResponse<RefundResponse>> list(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
                                                          @RequestParam(required = false) UUID bookingId,
                                                          @RequestParam(required = false) RefundStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return refundService.list(actorId, roles, bookingId, status, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RefundCreateResponse> create(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                                    @Valid @RequestBody RefundCreateRequest request) {
        return refundService.create(idempotencyKey, request);
    }

    @PostMapping("/complaints")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RefundCreateResponse> createComplaintRefund(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ComplaintRefundRequest request) {
        return refundService.createComplaintRefund(idempotencyKey, request);
    }

    @PostMapping("/{refundId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<RefundResponse> retry(@CurrentUserId UUID actorId, @PathVariable UUID refundId) {
        return refundService.retry(actorId, refundId);
    }
}
