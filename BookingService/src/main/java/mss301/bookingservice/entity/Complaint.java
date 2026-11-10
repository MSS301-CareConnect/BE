package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintLevel;
import mss301.bookingservice.enums.ComplaintTargetType;
import mss301.bookingservice.enums.ComplaintCategory;
import mss301.bookingservice.enums.ComplaintAction;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
@Entity
@Table(name = "complaints")
@AttributeOverride(name = "id", column = @Column(name = "complaint_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Complaint extends BaseEntity {
    @Column(name = "complaint_code", nullable = false, length = 30, unique = true)
    String complaintCode;
    @Column(name = "booking_id", nullable = false)
    UUID bookingId;
    @Column(name = "occurrence_id")
    UUID occurrenceId;
    @Column(name = "customer_id", nullable = false)
    UUID customerId;
    @Column(name = "center_id", nullable = false)
    UUID centerId;
    @Column(name = "caregiver_id")
    UUID caregiverId;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    ComplaintTargetType targetType;
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    ComplaintCategory category;
    @Column(name = "title", nullable = false, length = 200)
    String title;
    @Column(name = "content", nullable = false, length = 4000)
    String content;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    ComplaintStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    ComplaintLevel level;
    @Column(name = "resolution", length = 4000)
    String resolution;
    @Column(name = "resolved_by")
    UUID resolvedBy;
    @Column(name = "resolved_at")
    Instant resolvedAt;
    @Column(name = "escalated_at")
    Instant escalatedAt;
    @Column(name = "escalate_reason", length = 4000)
    String escalateReason;
    @Column(name = "refund_amount", precision = 14, scale = 2)
    BigDecimal refundAmount;
    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
