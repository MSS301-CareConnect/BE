package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.FeedbackVisibility;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedbacks", uniqueConstraints = @UniqueConstraint(name = "uk_feedback_booking_caregiver", columnNames = {"booking_id", "caregiver_id"}))
@AttributeOverride(name = "id", column = @Column(name = "feedback_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Feedback extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;
    @Column(name = "caregiver_id", nullable = false)
    UUID caregiverId;
    @Column(name = "caregiver_user_id", nullable = false)
    UUID caregiverUserId;
    @Column(name = "customer_id", nullable = false)
    UUID customerId;
    @Column(name = "center_id", nullable = false)
    UUID centerId;
    @Column(name = "customer_display_name", nullable = false, length = 255)
    String customerDisplayName;
    @Column(name = "rating", nullable = false)
    Short rating;
    @Column(name = "comment", length = 2000)
    String comment;
    @Column(name = "reply", length = 1000)
    String reply;
    @Column(name = "replied_by")
    UUID repliedBy;
    @Column(name = "replied_at")
    Instant repliedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    FeedbackVisibility visibility;
    @Column(name = "hidden_reason", length = 1000)
    String hiddenReason;
}
