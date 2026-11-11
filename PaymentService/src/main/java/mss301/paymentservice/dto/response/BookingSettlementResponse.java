package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.OccurrenceFinalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingSettlementResponse {
    UUID bookingId;

    UUID centerId;

    BigDecimal commissionPercent;

    BigDecimal paidAmount;

    BigDecimal refundedAmount;

    // Tiền buổi hoàn tất / khách vắng trung tâm nhận (đã trừ hoa hồng).
    BigDecimal centerServiceAmount;

    BigDecimal centerKeptDepositAmount;

    BigDecimal caregiverKeptDepositAmount;

    BigDecimal complaintDebitAmount;

    // Trung tâm thực nhận = tiền buổi + cọc giữ − trừ khiếu nại.
    BigDecimal centerNetAmount;

    // Hoa hồng nền tảng thực thu (đã trừ phần trả lại khi hoàn khiếu nại).
    BigDecimal commissionAmount;

    int settledCount;

    int pendingCount;

    List<Occurrence> occurrences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Occurrence {
        UUID occurrenceId;

        Instant startAt;

        BigDecimal priceAmount;

        BigDecimal paidAmount;

        BigDecimal refundedAmount;

        BigDecimal keptDepositAmount;

        BigDecimal centerAmount;

        BigDecimal commissionAmount;

        // null + settledAt null = chờ quyết toán; null + settledAt = buổi không tính tiền (hủy / hết hạn phía Booking).
        OccurrenceFinalStatus finalStatus;

        Instant settledAt;
    }
}
