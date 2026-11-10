package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Hồ sơ một lần caregiver vắng ca (2026-11-10): tạo cùng lúc buổi thành CAREGIVER_MISSED, mỗi buổi tối đa một dòng. Tiền
 * không nằm ở đây (khách đã được hoàn 100 % qua outbox REFUND) — đây là phần kỷ luật: caregiver giải trình, Center Manager
 * kết luận. Hai id caregiver chụp lúc vắng để hồ sơ không đổi khi các buổi sau sang người khác.
 */
@Entity
@Table(name = "caregiver_absences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "absence_id"))
public class CaregiverAbsence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occurrence_id", nullable = false)
    BookingOccurrence occurrence;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "caregiver_id", nullable = false)
    UUID caregiverId;

    @Column(name = "caregiver_user_id", nullable = false)
    UUID caregiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    CaregiverAbsenceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    CaregiverAbsenceStatus status;

    // Giải trình của caregiver — gửi lại được tới khi trung tâm kết luận.
    @Column(name = "explanation", columnDefinition = "text")
    String explanation;

    @Column(name = "explained_at")
    Instant explainedAt;

    @Column(name = "conclusion_note", columnDefinition = "text")
    String conclusionNote;

    @Column(name = "concluded_by")
    UUID concludedBy;

    @Column(name = "concluded_at")
    Instant concludedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
