package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
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

    // Giá buổi tại thời điểm đặt (BK-01); NULL với booking tạo trước khi có giá từng buổi.
    @Column(name = "price_amount", precision = 14, scale = 2)
    BigDecimal priceAmount;

    // Giờ caregiver thực tế bắt đầu / kết thúc — TODO(giai đoạn ca chăm sóc): chưa có API ghi.
    // Caregiver xác nhận nhận ca (OPS-01); xóa khi đổi caregiver.
    @Column(name = "acknowledged_at")
    Instant acknowledgedAt;

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

    // OPS-06: buổi MISSED do ai (căn cứ hoàn tiền).
    @Enumerated(EnumType.STRING)
    @Column(name = "missed_reason", length = 30)
    MissedReason missedReason;

    // Caregiver báo khách vắng mặt; job MISSED bỏ qua buổi đã báo, chờ manager quyết định.
    @Column(name = "no_show_reported_at")
    Instant noShowReportedAt;

    @Column(name = "no_show_reported_by")
    UUID noShowReportedBy;

    @Column(name = "no_show_note", columnDefinition = "text")
    String noShowNote;

    @Column(name = "no_show_decided_by")
    UUID noShowDecidedBy;

    @Column(name = "no_show_decided_at")
    Instant noShowDecidedAt;

    // Đã nhắc manager báo vắng chưa được quyết định — chỉ nhắc một lần.
    @Column(name = "no_show_reminded_at")
    Instant noShowRemindedAt;

    // BK-03 / BK-04: buổi bị hủy lúc nào, bởi bên nào (căn cứ hoàn tiền / lịch sử).
    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by_party", length = 20)
    CancelledBy cancelledByParty;

    // Caregiver của buổi bị khóa (BK-04 CAREGIVER_DEACTIVATED) → manager phải đổi người; đổi xong thì bỏ cờ.
    @Builder.Default
    @Column(name = "needs_reassignment", nullable = false)
    Boolean needsReassignment = false;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    // Hai id caregiver luôn đổi cùng nhau. Người mới chưa nhận ca → buổi đã ACKNOWLEDGED quay về SCHEDULED (OPS-01).
    public void assignCaregiver(UUID newCaregiverId, UUID newCaregiverUserId) {
        if (!Objects.equals(caregiverId, newCaregiverId) && status == OccurrenceStatus.ACKNOWLEDGED) {
            status = OccurrenceStatus.SCHEDULED;
            acknowledgedAt = null;
        }
        if (!Objects.equals(caregiverId, newCaregiverId)) {
            needsReassignment = false;
        }
        caregiverId = newCaregiverId;
        caregiverUserId = newCaregiverUserId;
    }
}
