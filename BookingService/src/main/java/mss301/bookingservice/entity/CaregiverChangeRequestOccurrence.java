package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "caregiver_change_request_occurrences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeRequestOccurrence {

    @EmbeddedId
    CaregiverChangeRequestOccurrenceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("requestId")
    @JoinColumn(name = "request_id", nullable = false)
    CaregiverChangeRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("occurrenceId")
    @JoinColumn(name = "occurrence_id", nullable = false)
    BookingOccurrence occurrence;
}
