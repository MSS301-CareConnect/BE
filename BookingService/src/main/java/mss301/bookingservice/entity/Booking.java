package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.ScheduleType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Thông tin chung + quy tắc lịch của một lần đặt. Mỗi buổi chăm sóc là một {@link BookingOccurrence}.
 * Tham chiếu service khác bằng UUID, không FK: customer/caregiverUser = Identity, recipient = CareRecipient,
 * center/caregiver = Provider. Không có API xóa — deleted_at chỉ do kế thừa BaseEntity.
 */
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
    @Column(name = "schedule_type", nullable = false, length = 20)
    ScheduleType scheduleType;

    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalTime endTime;

    // ISO 1 = Thứ Hai … 7 = Chủ Nhật, tăng dần; chỉ có ở lịch WEEKLY.
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

    @Column(name = "deposit_amount", precision = 14, scale = 2)
    BigDecimal depositAmount;

    @Column(name = "total_amount", precision = 14, scale = 2)
    BigDecimal totalAmount;

    // Hạn trung tâm duyệt (PENDING); quá hạn → EXPIRED.
    @Column(name = "acceptance_due_at")
    Instant acceptanceDueAt;

    @Column(name = "accepted_by")
    UUID acceptedBy;

    @Column(name = "accepted_at")
    Instant acceptedAt;

    // Hạn đặt cọc (AWAITING_DEPOSIT); quá hạn → EXPIRED.
    @Column(name = "deposit_due_at")
    Instant depositDueAt;

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
