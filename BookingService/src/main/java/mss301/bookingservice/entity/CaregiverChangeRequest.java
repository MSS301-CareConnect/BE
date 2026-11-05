package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverChangeReasonCode;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.enums.CaregiverChangeScopeType;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "caregiver_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Column(name = "customer_user_id", nullable = false)
    UUID customerUserId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    CaregiverChangeScopeType scopeType;

    @Column(name = "from_date")
    LocalDate fromDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 50)
    CaregiverChangeReasonCode reasonCode;

    @Column(name = "reason_detail", columnDefinition = "text")
    String reasonDetail;

    @Column(name = "preferred_caregiver_id")
    UUID preferredCaregiverId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    CaregiverChangeRequestStatus status = CaregiverChangeRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    UUID reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    String rejectionReason;

    @Builder.Default
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CaregiverChangeRequestOccurrence> occurrences = new ArrayList<>();
}
