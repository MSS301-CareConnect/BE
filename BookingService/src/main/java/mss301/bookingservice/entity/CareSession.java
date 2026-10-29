package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ca chăm sóc thực tế của một buổi: caregiver check-in (OPS-02) → làm checklist → check-out / trung tâm buộc kết thúc
 * (OPS-04). Mỗi buổi tối đa một session (UNIQUE occurrence_id chặn hai lần check-in song song).
 */
@Entity
@Table(name = "care_sessions")
@AttributeOverride(name = "id", column = @Column(name = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSession extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occurrence_id", nullable = false, unique = true)
    BookingOccurrence occurrence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Column(name = "caregiver_id", nullable = false)
    UUID caregiverId;

    @Column(name = "caregiver_user_id", nullable = false)
    UUID caregiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    CareSessionStatus status;

    @Column(name = "check_in_at", nullable = false)
    Instant checkInAt;

    @Column(name = "check_out_at")
    Instant checkOutAt;

    // Tọa độ chỉ lưu để đối chiếu khi có khiếu nại — không kiểm khoảng cách.
    @Column(name = "check_in_lat", precision = 9, scale = 6)
    BigDecimal checkInLatitude;

    @Column(name = "check_in_lng", precision = 9, scale = 6)
    BigDecimal checkInLongitude;

    @Column(name = "check_out_lat", precision = 9, scale = 6)
    BigDecimal checkOutLatitude;

    @Column(name = "check_out_lng", precision = 9, scale = 6)
    BigDecimal checkOutLongitude;

    // Người kết thúc ca: caregiver (check-out) hoặc manager (buộc kết thúc).
    @Column(name = "ended_by")
    UUID endedBy;

    @Column(name = "end_reason", columnDefinition = "text")
    String endReason;

    // Đã nhắc "quên kết thúc ca" — chỉ nhắc một lần.
    @Column(name = "overdue_reminded_at")
    Instant overdueRemindedAt;

    // D21: hệ thống tự đóng ca quên check-out (không phải caregiver / manager kết thúc).
    @Column(name = "auto_closed", nullable = false)
    boolean autoClosed;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
