package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

// PUT /api/v1/center-booking-policies/{centerId} — thay toàn bộ policy (spec §8). Mốc thời gian theo phút (tối đa 30
// ngày), tỷ lệ theo phần trăm. centerId lấy từ path; manager chỉ sửa trung tâm trong token. version: null khi trung tâm
// chưa có policy riêng, ngược lại phải khớp bản đang lưu.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterBookingPolicyRequest {
    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer shiftMinAdvanceMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer recurringMinAdvanceMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer customMinAdvanceMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 1, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer confirmationTimeoutMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 1, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer assignmentTimeoutMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 1, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer initialPaymentTimeoutMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer remainingPaymentDueBeforeMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Size(max = 5, message = "INVALID_BOOKING_POLICY")
    List<@NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED") @Min(value = 1, message = "INVALID_BOOKING_POLICY")
            @Max(value = 43200, message = "INVALID_BOOKING_POLICY") Integer> remainingPaymentReminderMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @DecimalMin(value = "1", message = "INVALID_BOOKING_POLICY")
    @DecimalMax(value = "100", message = "INVALID_BOOKING_POLICY")
    BigDecimal depositPercent;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 1440, message = "INVALID_BOOKING_POLICY")
    Integer checkInEarlyMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 1440, message = "INVALID_BOOKING_POLICY")
    Integer checkInLateMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 43200, message = "INVALID_BOOKING_POLICY")
    Integer refundThresholdMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @DecimalMin(value = "0", message = "INVALID_BOOKING_POLICY")
    @DecimalMax(value = "100", message = "INVALID_BOOKING_POLICY")
    BigDecimal refundPercentBeforeThreshold;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @DecimalMin(value = "0", message = "INVALID_BOOKING_POLICY")
    @DecimalMax(value = "100", message = "INVALID_BOOKING_POLICY")
    BigDecimal refundPercentAfterThreshold;

    // 2026-11-10: khách hủy khi còn dưới mốc này thì không được hoàn (caregiver đã giữ ca); không vượt refundThresholdMinutes
    // (kiểm ở service).
    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @Min(value = 0, message = "INVALID_BOOKING_POLICY")
    @Max(value = 1440, message = "INVALID_BOOKING_POLICY")
    Integer lateCancelNoRefundMinutes;

    @NotNull(message = "BOOKING_POLICY_VALUE_REQUIRED")
    @DecimalMin(value = "0", message = "INVALID_BOOKING_POLICY")
    @DecimalMax(value = "100", message = "INVALID_BOOKING_POLICY")
    BigDecimal overduePaymentForfeitPercent;

    Long version;
}
