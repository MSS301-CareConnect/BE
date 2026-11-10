package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.BookingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Dữ liệu PaymentService cần để tạo phiên cọc / tính hoàn tiền (C3.2, token SERVICE).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentContextResponse {
    UUID bookingId;

    String bookingCode;

    UUID customerId;

    UUID centerId;

    BookingStatus status;

    // Spec §9.1: SHIFT trả 100 %, RECURRING / CUSTOM cọc theo policy rồi trả phần còn lại trước remainingPaymentDueAt.
    BookingType bookingType;

    BigDecimal initialPaymentPercent;

    Instant remainingPaymentDueAt;

    Instant fullyPaidAt;

    // Center booking policy hiệu lực (spec §8, §10.2) — PaymentService chụp vào snapshot lúc mở phiên thanh toán đầu để
    // tính hoàn 100 / 70 và phần cọc bị giữ khi quá hạn trả phần còn lại.
    Integer refundThresholdMinutes;

    BigDecimal refundPercentBeforeThreshold;

    BigDecimal refundPercentAfterThreshold;

    // 2026-11-10: khách hủy còn dưới mốc này → không hoàn (PaymentService chụp vào policy_snapshot).
    Integer lateCancelNoRefundMinutes;

    BigDecimal overduePaymentForfeitPercent;

    BigDecimal totalAmount;

    Instant depositDueAt;

    Instant firstStartAt;

    Instant depositPaidAt;

    List<PaymentContextOccurrenceResponse> occurrences;
}
