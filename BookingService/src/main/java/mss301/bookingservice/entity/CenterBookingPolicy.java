package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Center booking policy (spec §8): mỗi trung tâm tối đa một dòng; chưa có dòng thì dùng mặc định
 * booking.policy-defaults. Mốc thời gian lưu theo phút, tỷ lệ theo phần trăm. Booking đọc policy lúc xử lý.
 */
@Entity
@Table(name = "center_booking_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "policy_id"))
public class CenterBookingPolicy extends BaseEntity {

    @Column(name = "center_id", nullable = false, unique = true)
    UUID centerId;

    // Khoảng tối thiểu từ lúc đặt tới buổi đầu, theo loại booking.
    @Column(name = "shift_min_advance_minutes", nullable = false)
    Integer shiftMinAdvanceMinutes;

    @Column(name = "recurring_min_advance_minutes", nullable = false)
    Integer recurringMinAdvanceMinutes;

    @Column(name = "custom_min_advance_minutes", nullable = false)
    Integer customMinAdvanceMinutes;

    @Column(name = "confirmation_timeout_minutes", nullable = false)
    Integer confirmationTimeoutMinutes;

    @Column(name = "assignment_timeout_minutes", nullable = false)
    Integer assignmentTimeoutMinutes;

    @Column(name = "initial_payment_timeout_minutes", nullable = false)
    Integer initialPaymentTimeoutMinutes;

    // RECURRING / CUSTOM phải trả xong phần còn lại trước buổi đầu khoảng này.
    @Column(name = "remaining_payment_due_before_minutes", nullable = false)
    Integer remainingPaymentDueBeforeMinutes;

    // Nhắc trả phần còn lại trước hạn (M6), mỗi phần tử là số phút trước hạn.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "remaining_payment_reminder_minutes", nullable = false)
    Integer[] remainingPaymentReminderMinutes;

    // Tỷ lệ cọc cho RECURRING / CUSTOM; SHIFT luôn thanh toán 100 % (spec §4).
    @Column(name = "deposit_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal depositPercent;

    @Column(name = "check_in_early_minutes", nullable = false)
    Integer checkInEarlyMinutes;

    @Column(name = "check_in_late_minutes", nullable = false)
    Integer checkInLateMinutes;

    // Khách hủy buổi trước giờ bắt đầu ít nhất mốc này → hoàn refundPercentBeforeThreshold, ngược lại
    // refundPercentAfterThreshold (spec §10.2, Q5) — tính trên tiền đã trả của buổi (M4).
    @Column(name = "refund_threshold_minutes", nullable = false)
    Integer refundThresholdMinutes;

    @Column(name = "refund_percent_before_threshold", nullable = false, precision = 5, scale = 2)
    BigDecimal refundPercentBeforeThreshold;

    @Column(name = "refund_percent_after_threshold", nullable = false, precision = 5, scale = 2)
    BigDecimal refundPercentAfterThreshold;

    // Quá hạn trả phần còn lại → booking EXPIRED, khách mất tỷ lệ này của tiền cọc (M3).
    @Column(name = "overdue_payment_forfeit_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal overduePaymentForfeitPercent;

    @Column(name = "updated_by")
    UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
