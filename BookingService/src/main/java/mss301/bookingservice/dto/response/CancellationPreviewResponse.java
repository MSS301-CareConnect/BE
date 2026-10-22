package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// BK-03: số tiền hoàn / phí nếu hủy các buổi — gửi previewToken kèm lệnh hủy để chắc khách đồng ý đúng số này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CancellationPreviewResponse {
    List<Item> items;

    BigDecimal totalRefund;

    BigDecimal totalFee;

    String previewToken;

    Instant expiresAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {
        UUID occurrenceId;

        Instant startAt;

        BigDecimal priceAmount;

        BigDecimal paidAmount;

        BigDecimal feeAmount;

        BigDecimal refundAmount;

        // Mốc chính sách áp dụng (PaymentService); NOT_PAID khi booking chưa đặt cọc.
        String rule;
    }
}
