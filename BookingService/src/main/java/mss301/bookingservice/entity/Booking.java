package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.enums.BookingExpiredReason;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "booking_id"))
public class Booking extends BaseEntity {

    @Column(name = "booking_code", nullable = false, unique = true, length = 20)
    String bookingCode;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "recipient_id", nullable = false)
    UUID recipientId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    // caregiver_profiles.caregiver_id bên ProviderService (tham chiếu nghiệp vụ chính)
    @Column(name = "caregiver_id")
    UUID caregiverId;

    // user_id của caregiver bên IdentityService (= sub JWT) — luôn ghi cùng caregiverId, lấy từ Provider
    @Column(name = "caregiver_user_id")
    UUID caregiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 20)
    BookingType bookingType;

    // Chỉ RECURRING.
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 20)
    RecurrenceFrequency recurrenceFrequency;

    // Ngày (theo timezone) của buổi đầu / buổi cuối — mọi loại booking.
    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    LocalDate endDate;

    // Khung giờ lặp — chỉ RECURRING (SHIFT theo lịch caregiver, CUSTOM mỗi buổi một giờ).
    @Column(name = "start_time")
    LocalTime startTime;

    @Column(name = "end_time")
    LocalTime endTime;

    // ISO 1 = Thứ Hai … 7 = Chủ Nhật, tăng dần; chỉ có ở RECURRING WEEKLY.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week")
    Short[] daysOfWeek;

    @Column(name = "timezone", nullable = false, length = 64)
    String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_snapshot", nullable = false)
    RecipientSnapshot recipientSnapshot;

    @Column(name = "service_address", nullable = false, columnDefinition = "text")
    String serviceAddress;

    @Column(name = "customer_note", columnDefinition = "text")
    String customerNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    BookingStatus status;

    // NULL khi chưa nối CareCatalogService (không tin giá từ client).
    @Column(name = "subtotal", precision = 14, scale = 2)
    BigDecimal subtotal;

    // Khoản thanh toán đầu — PaymentService ghi khi xác nhận (PAY-08); tới đợt 4 vẫn do Payment tính.
    @Column(name = "deposit_amount", precision = 14, scale = 2)
    BigDecimal depositAmount;

    // Tỷ lệ khoản thanh toán đầu chụp từ center policy lúc tạo: SHIFT 100, RECURRING / CUSTOM = cọc % (spec §4, §9.1).
    @Column(name = "initial_payment_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal initialPaymentPercent;

    // Cửa sổ check-in chụp từ center policy lúc tạo (spec §8, Q12): sớm nhất start − early, muộn nhất start + late;
    // quá start + late chưa check-in → CAREGIVER_MISSED. Đổi policy sau không đổi cửa sổ của booking đã đặt.
    @Column(name = "check_in_early_minutes", nullable = false)
    Integer checkInEarlyMinutes;

    @Column(name = "check_in_late_minutes", nullable = false)
    Integer checkInLateMinutes;

    @Column(name = "total_amount", precision = 14, scale = 2)
    BigDecimal totalAmount;

    // Hạn trung tâm xác nhận (PENDING_CENTER_CONFIRMATION); quá hạn → EXPIRED.
    @Column(name = "acceptance_due_at")
    Instant acceptanceDueAt;

    @Column(name = "accepted_by")
    UUID acceptedBy;

    @Column(name = "accepted_at")
    Instant acceptedAt;

    // Hạn phân caregiver (PENDING_CAREGIVER_ASSIGNMENT); quá hạn → EXPIRED.
    @Column(name = "assignment_due_at")
    Instant assignmentDueAt;

    // Hạn thanh toán khoản đầu (AWAITING_INITIAL_PAYMENT); quá hạn → EXPIRED.
    @Column(name = "deposit_due_at")
    Instant depositDueAt;

    // RECURRING / CUSTOM: hạn trả xong phần còn lại = buổi đầu − remainingPaymentDueBefore của policy (spec §9.1); SHIFT
    // trả đủ 100 % nên NULL. Quá hạn: PaymentService báo /payment-overdue → EXPIRED.
    @Column(name = "remaining_payment_due_at")
    Instant remainingPaymentDueAt;

    // PaymentService báo đã trả đủ (khoản đầu 100 % hoặc phần còn lại). Check-in cần đã trả đủ khi có remainingPaymentDueAt.
    @Column(name = "fully_paid_at")
    Instant fullyPaidAt;

    // Mốc nhắc trả phần còn lại (phút trước hạn) nhỏ nhất đã gửi hoặc đã qua lúc trả khoản đầu — mỗi mốc nhắc một lần.
    @Column(name = "remaining_reminder_minutes")
    Integer remainingReminderMinutes;

    // Mốc nhắc trung tâm (phút trước hạn xác nhận / phân công) nhỏ nhất đã gửi hoặc đã qua khi bắt đầu chờ.
    @Column(name = "center_reminder_minutes")
    Integer centerReminderMinutes;

    // Tự phân công (2026-11-09): lần thử gần nhất (scheduler thử lại sau booking.schedule.auto-assign-retry) và lần gần nhất
    // báo trung tâm "chưa tìm được người" (lần đầu rồi theo các mốc booking.schedule.unassigned-remind-before).
    @Column(name = "auto_assign_attempted_at")
    Instant autoAssignAttemptedAt;

    @Column(name = "unassigned_notified_at")
    Instant unassignedNotifiedAt;

    // Bên chưa làm kịp khi booking EXPIRED; NULL ở trạng thái khác.
    @Enumerated(EnumType.STRING)
    @Column(name = "expired_reason", length = 40)
    BookingExpiredReason expiredReason;

    // Buổi đầu tiên check-in (OPS-02).
    @Column(name = "started_at")
    Instant startedAt;

    // Mọi buổi đã kết thúc, có ít nhất một buổi được phục vụ (OPS-04).
    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "confirmed_at")
    Instant confirmedAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "cancelled_by")
    UUID cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    String cancelReason;

    @Column(name = "rejected_by")
    UUID rejectedBy;

    @Column(name = "rejected_at")
    Instant rejectedAt;

    @Column(name = "rejected_reason", columnDefinition = "text")
    String rejectedReason;

    @Column(name = "idempotency_key", length = 100)
    String idempotencyKey;

    // SHA-256 (hex) của request đã chuẩn hóa — cùng key khác nội dung → IDEMPOTENCY_KEY_REUSED.
    @Column(name = "idempotency_hash", length = 64)
    String idempotencyHash;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
