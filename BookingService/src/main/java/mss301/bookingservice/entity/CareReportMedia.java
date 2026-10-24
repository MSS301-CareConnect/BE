package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;

@Entity
@Table(name = "care_report_media")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareReportMedia {
    @EmbeddedId
    CareReportMediaId id;
    @Column(name = "display_order", nullable = false)
    Integer displayOrder;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
