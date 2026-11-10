package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Policy hiệu lực của một trung tâm. customized = false: trung tâm chưa có policy riêng, đang dùng mặc định
// (version / updatedAt / updatedBy null).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterBookingPolicyResponse {
    UUID centerId;
    boolean customized;
    Integer shiftMinAdvanceMinutes;
    Integer recurringMinAdvanceMinutes;
    Integer customMinAdvanceMinutes;
    Integer confirmationTimeoutMinutes;
    Integer assignmentTimeoutMinutes;
    Integer initialPaymentTimeoutMinutes;
    Integer remainingPaymentDueBeforeMinutes;
    List<Integer> remainingPaymentReminderMinutes;
    BigDecimal depositPercent;
    Integer checkInEarlyMinutes;
    Integer checkInLateMinutes;
    Integer refundThresholdMinutes;
    BigDecimal refundPercentBeforeThreshold;
    BigDecimal refundPercentAfterThreshold;
    Integer lateCancelNoRefundMinutes;
    BigDecimal overduePaymentForfeitPercent;
    Long version;
    Instant updatedAt;
    UUID updatedBy;
}
