package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareReportStatus;
import mss301.bookingservice.enums.OverallCondition;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_reports")
@AttributeOverride(name = "id", column = @Column(name = "report_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareReport extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    CareSession session;
    @Column(name = "occurrence_id", nullable = false)
    UUID occurrenceId;
    @Column(name = "booking_id", nullable = false)
    UUID bookingId;
    @Column(name = "caregiver_user_id", nullable = false)
    UUID caregiverUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_condition", nullable = false, length = 30)
    OverallCondition overallCondition;
    @Column(name = "summary", nullable = false, length = 4000)
    String summary;
    @Column(name = "meal_notes", length = 2000)
    String mealNotes;
    @Column(name = "medication_notes", length = 2000)
    String medicationNotes;
    @Column(name = "mobility_notes", length = 2000)
    String mobilityNotes;
    @Column(name = "mood_notes", length = 2000)
    String moodNotes;
    @Column(name = "recommendations", length = 2000)
    String recommendations;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    CareReportStatus status;
    @Column(name = "submitted_at")
    Instant submittedAt;
    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
