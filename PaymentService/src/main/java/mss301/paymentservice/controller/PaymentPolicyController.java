package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-policies")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment policies", description = "Chính sách cọc áp dụng khi tạo booking")
public class PaymentPolicyController {
    PaymentService service;

    @GetMapping("/effective")
    @Operation(summary = "Lấy chính sách cọc đang áp dụng", description = "Trả về phần trăm tiền cọc cho trung tâm tại một thời điểm. BookingService gọi endpoint này khi tính booking.")
    public APIResponse<PaymentPolicyResponse> effective(@RequestParam UUID centerId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return APIResponse.success("Lấy chính sách thanh toán thành công", service.getEffectivePolicy(centerId, at));
    }
}
