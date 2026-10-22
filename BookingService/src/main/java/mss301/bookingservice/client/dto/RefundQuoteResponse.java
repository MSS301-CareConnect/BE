package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Bản sao response C2.2 của PaymentService: số đã trả / phí / hoàn từng buổi theo chính sách snapshot lúc cọc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundQuoteResponse {
    List<Item> items;

    BigDecimal totalPaid;

    BigDecimal totalFee;

    BigDecimal totalRefund;

    UUID policyId;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        UUID occurrenceId;

        BigDecimal priceAmount;

        BigDecimal paidAmount;

        BigDecimal feeAmount;

        BigDecimal refundAmount;

        // Mốc chính sách áp dụng (vd TIER_24_72) — chuỗi để không vỡ khi Payment thêm mốc mới.
        String rule;
    }
}
