package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.request.RemainingInvoiceRequest;
import mss301.paymentservice.dto.request.WaiveInvoiceRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResultResponse;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.RemainingInvoiceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/remaining-invoices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Remaining invoices", description = "Hóa đơn phần còn lại sau khi booking hoàn thành: tạo, thanh toán, miễn nợ")
public class RemainingInvoiceController {
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    RemainingInvoiceService remainingInvoiceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public APIResponse<PageResponse<RemainingInvoiceResponse>> list(@CurrentUserId UUID actorId,
                                                                    @CurrentRoles Set<RoleCode> roles,
                                                                    @RequestParam(required = false) UUID customerId,
                                                                    @RequestParam(required = false) InvoiceStatus status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return remainingInvoiceService.list(actorId, roles, customerId, status, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RemainingInvoiceResultResponse> create(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RemainingInvoiceRequest request) {
        return remainingInvoiceService.create(idempotencyKey, request);
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CheckoutResponse> pay(@CurrentUserId UUID customerId, @PathVariable UUID invoiceId,
                                             @Valid @RequestBody CreateCheckoutRequest request,
                                             HttpServletRequest httpServletRequest) {
        return remainingInvoiceService.pay(customerId, invoiceId, request, httpServletRequest);
    }

    @PatchMapping("/{invoiceId}/waive")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<RemainingInvoiceResponse> waive(@CurrentUserId UUID actorId, @PathVariable UUID invoiceId,
                                                       @Valid @RequestBody WaiveInvoiceRequest request) {
        return remainingInvoiceService.waive(actorId, invoiceId, request);
    }
}
