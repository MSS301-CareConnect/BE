package mss301.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeRequestOccurrenceId implements Serializable {

    @Column(name = "request_id")
    UUID requestId;

    @Column(name = "occurrence_id")
    UUID occurrenceId;
}
