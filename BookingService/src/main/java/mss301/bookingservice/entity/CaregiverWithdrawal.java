package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverWithdrawalReason;

import java.util.UUID;

@Entity
@Table(name = "caregiver_withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "withdrawal_id"))
public class CaregiverWithdrawal extends BaseEntity {

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
    @Column(name = "reason_code", nullable = false, length = 20)
    CaregiverWithdrawalReason reasonCode;

    @Column(name = "reason_detail", columnDefinition = "text")
    String reasonDetail;
}
