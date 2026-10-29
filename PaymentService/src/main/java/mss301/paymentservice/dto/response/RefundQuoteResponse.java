package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Hợp đồng C2.2 — tên field trùng RefundQuoteResponse của BookingService.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundQuoteResponse {
    List<Item> items;

    BigDecimal totalPaid;

    BigDecimal totalFee;

    BigDecimal totalRefund;

    UUID policyId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {
        UUID occurrenceId;

        BigDecimal priceAmount;

        BigDecimal paidAmount;

        BigDecimal feeAmount;

        BigDecimal refundAmount;

        String rule;
    }
}
