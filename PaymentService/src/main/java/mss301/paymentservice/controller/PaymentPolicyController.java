package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PaymentPolicyResponse;
import mss301.paymentservice.service.PaymentPolicyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-policies")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment policies", description = "Chính sách cọc / hoàn tiền đang hiệu lực (chỉ đọc — D15)")
public class PaymentPolicyController {
    PaymentPolicyService paymentPolicyService;

    @GetMapping("/effective")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PaymentPolicyResponse> getEffective(@RequestParam(required = false) UUID centerId,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return paymentPolicyService.getEffective(centerId, at);
    }
}
