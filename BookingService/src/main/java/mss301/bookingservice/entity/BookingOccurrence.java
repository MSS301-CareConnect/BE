package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Một buổi chăm sóc (thay vai trò care session): giờ theo lịch lưu UTC, caregiver mặc định theo booking nhưng đổi
 * riêng được. Hủy buổi = CANCELLED (không xóa mềm; deleted_at chỉ do kế thừa BaseEntity).
 */
@Entity
@Table(name = "booking_occurrences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "occurrence_id"))
public class BookingOccurrence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Column(name = "occurrence_no", nullable = false)
    Integer occurrenceNo;

    @Column(name = "start_at", nullable = false)
    Instant startAt;

    @Column(name = "end_at", nullable = false)
    Instant endAt;

    @Column(name = "duration_minutes", nullable = false)
    Integer durationMinutes;

    // Giờ caregiver thực tế bắt đầu / kết thúc — TODO(giai đoạn ca chăm sóc): chưa có API ghi.
    @Column(name = "actual_start_at")
    Instant actualStartAt;

    @Column(name = "actual_end_at")
    Instant actualEndAt;

    @Column(name = "caregiver_id")
    UUID caregiverId;

    @Column(name = "caregiver_user_id")
    UUID caregiverUserId;

    // Tham chiếu caregiver_schedules.schedule_id bên Provider (khung giờ làm việc theo ngày) — chỉ tham chiếu,
    // Provider không đánh dấu đã đặt. TODO(Provider): gán khi có API kiểm khung giờ làm việc.
    @Column(name = "availability_slot_id")
    UUID availabilitySlotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    OccurrenceStatus status;

    @Column(name = "cancel_reason", columnDefinition = "text")
    String cancelReason;

    @Column(name = "note", columnDefinition = "text")
    String note;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
