package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.RefundQuoteRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.RefundQuoteResponse;
import mss301.paymentservice.service.RefundService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Resource riêng /refund-quotes (hợp đồng C2.2) nhưng dùng chung RefundService — không ghi DB.
@RestController
@RequestMapping("/api/v1/refund-quotes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Refund quotes", description = "Preview số tiền hoàn khi hủy buổi theo chính sách đã snapshot (BookingService gọi)")
public class RefundQuoteController {
    RefundService refundService;

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RefundQuoteResponse> quote(@Valid @RequestBody RefundQuoteRequest request) {
        return refundService.quote(request);
    }
}
