package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.ReplacementConsentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    // SHIFT: caregiver_schedules.schedule_id bên Provider mà buổi giữ — Provider không đánh dấu đã đặt; Booking giới hạn
    // số booking trên một ca theo max_bookings (2026-11-05).
    @Column(name = "caregiver_schedule_id")
    UUID caregiverScheduleId;

    // Đa dịch vụ theo khung giờ (2026-11-05): buổi chỉ làm dịch vụ này; NULL = mọi dịch vụ của booking.
    @Column(name = "care_service_id")
    UUID careServiceId;

    // Địa chỉ riêng của buổi (2026-11-05: đổi địa chỉ theo buổi); NULL = địa chỉ của booking.
    @Column(name = "service_address", columnDefinition = "text")
    String serviceAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    OccurrenceStatus status;

    @Column(name = "cancel_reason", columnDefinition = "text")
    String cancelReason;

    @Column(name = "note", columnDefinition = "text")
    String note;

    // OPS-06: buổi CAREGIVER_MISSED do ai (căn cứ hoàn tiền).
    @Enumerated(EnumType.STRING)
    @Column(name = "missed_reason", length = 30)
    MissedReason missedReason;

    // Caregiver báo khách vắng mặt; job CAREGIVER_MISSED bỏ qua buổi đã báo, chờ manager quyết định.
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

    // Spec v3: Dời lịch (reschedule) & thay thế người khẩn cấp
    @Builder.Default
    @Column(name = "reschedule_count", nullable = false)
    Integer rescheduleCount = 0;

    @Column(name = "original_start_at")
    Instant originalStartAt;

    @Column(name = "original_end_at")
    Instant originalEndAt;

    @Builder.Default
    @Column(name = "is_emergency_replacement", nullable = false)
    Boolean isEmergencyReplacement = false;

    @Column(name = "replacement_reason", columnDefinition = "text")
    String replacementReason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "replacement_consent_status", length = 30)
    ReplacementConsentStatus replacementConsentStatus = ReplacementConsentStatus.NONE;

    @Column(name = "reminder_sent_at")
    Instant reminderSentAt;

    // 2026-11-10: đã cảnh báo "tới giờ mà chưa check-in" cho buổi này (một lần); xóa khi đổi người / dời lịch.
    @Column(name = "late_warned_at")
    Instant lateWarnedAt;

    // 2026-11-11: khách báo nhanh về buổi (chưa thấy người đến / không thực hiện đúng) — một lần mỗi buổi.
    @Column(name = "customer_reported_at")
    Instant customerReportedAt;

    @Column(name = "customer_report_note", columnDefinition = "text")
    String customerReportNote;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    // Hai id caregiver luôn đổi cùng nhau. Người mới chưa nhận ca → buổi đã ACKNOWLEDGED quay về SCHEDULED (OPS-01);
    // buổi chưa có caregiver được giữ lịch (PENDING_ASSIGNMENT → RESERVED, spec §11.2). 2026-11-09: khách cọc trước khi có
    // người → buổi của booking đã cọc được lên lịch chính thức ngay khi gán (PENDING_ASSIGNMENT → SCHEDULED).
    public void assignCaregiver(UUID newCaregiverId, UUID newCaregiverUserId) {
        if (!Objects.equals(caregiverId, newCaregiverId) && status == OccurrenceStatus.ACKNOWLEDGED) {
            status = OccurrenceStatus.SCHEDULED;
            acknowledgedAt = null;
        }
        if (status == OccurrenceStatus.PENDING_ASSIGNMENT) {
            status = BookingStatus.DEPOSIT_PAID.contains(booking.getStatus())
                    ? OccurrenceStatus.SCHEDULED
                    : OccurrenceStatus.RESERVED;
        }
        if (!Objects.equals(caregiverId, newCaregiverId)) {
            needsReassignment = false;
            reminderSentAt = null;
            lateWarnedAt = null;
            // Lịch Provider gắn với caregiver cũ (SHIFT) — người mới không giữ lịch đó.
            caregiverScheduleId = null;
        }
        caregiverId = newCaregiverId;
        caregiverUserId = newCaregiverUserId;
    }

    // CR-3: caregiver khách chọn không nhận đơn → buổi đang giữ (RESERVED) trả lịch, chờ trung tâm phân người khác.
    public void releaseCaregiver() {
        status = OccurrenceStatus.PENDING_ASSIGNMENT;
        caregiverId = null;
        caregiverUserId = null;
        caregiverScheduleId = null;
        needsReassignment = false;
        reminderSentAt = null;
    }
}
