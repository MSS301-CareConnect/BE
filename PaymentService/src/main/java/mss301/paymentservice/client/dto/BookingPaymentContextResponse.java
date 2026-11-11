package mss301.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.BookingStatus;
import mss301.paymentservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bản sao PaymentContextResponse của BookingService (GET /api/v1/bookings/{id}/payment-context, token SERVICE).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingPaymentContextResponse {
    UUID bookingId;

    String bookingCode;

    UUID customerId;

    UUID centerId;

    BookingStatus status;

    // Spec §9.1: % khoản thanh toán đầu (SHIFT 100) và hạn trả phần còn lại (RECURRING / CUSTOM). NULL = booking cũ
    // (trước spec v1): cọc theo platform_policies, phần còn lại thu sau khi hoàn tất.
    BigDecimal initialPaymentPercent;

    Instant remainingPaymentDueAt;

    // Center booking policy (spec §8, §10.2), chụp vào policy_snapshot lúc mở phiên thanh toán đầu.
    Integer refundThresholdMinutes;

    BigDecimal refundPercentBeforeThreshold;

    BigDecimal refundPercentAfterThreshold;

    // 2026-11-10: khách hủy còn dưới mốc này → không hoàn. Booking cũ không gửi → snapshot để trống, tính như 120 phút.
    Integer lateCancelNoRefundMinutes;

    BigDecimal overduePaymentForfeitPercent;

    BigDecimal totalAmount;

    Instant depositDueAt;

    Instant firstStartAt;

    Instant depositPaidAt;

    List<Occurrence> occurrences;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Occurrence {
        UUID occurrenceId;

        Integer occurrenceNo;

        Instant startAt;

        Instant endAt;

        OccurrenceStatus status;

        BigDecimal priceAmount;

        // CR-4: caregiver đang được giao buổi (NULL = chưa có người).
        UUID caregiverId;

        UUID caregiverUserId;

        boolean hasSession;

        // 2026-11-11: ca tự đóng đang chờ trung tâm xác nhận — chưa quyết toán buổi này.
        // Boolean (không phải boolean): BookingService bản cũ không gửi field này.
        Boolean settlementHeld;
    }
}
