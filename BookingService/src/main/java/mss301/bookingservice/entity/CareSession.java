package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.enums.SessionCompletionReview;
import mss301.bookingservice.enums.SessionLocationFlag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    // Tọa độ không chặn check-in; lệch xa địa chỉ chăm sóc / thiếu tọa độ thì gắn cờ locationFlag cho trung tâm xem lại.
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

    // 2026-11-11: số phút check-in sau giờ bắt đầu buổi / check-out trước giờ kết thúc buổi — chỉ ghi nhận, không đổi tiền.
    @Column(name = "late_minutes", nullable = false)
    int lateMinutes;

    @Column(name = "early_leave_minutes")
    Integer earlyLeaveMinutes;

    // Khoảng cách từ vị trí check-in tới địa chỉ chăm sóc; NULL = không đối chiếu được (thiếu tọa độ một trong hai bên).
    @Column(name = "check_in_distance_m")
    Integer checkInDistanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_flag", length = 20)
    SessionLocationFlag locationFlag;

    // Ca tự đóng: PENDING tới khi trung tâm xác nhận (PaymentService chưa quyết toán buổi trong lúc chờ).
    @Enumerated(EnumType.STRING)
    @Column(name = "completion_review", length = 20)
    SessionCompletionReview completionReview;

    @Column(name = "reviewed_by")
    UUID reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    String reviewNote;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
