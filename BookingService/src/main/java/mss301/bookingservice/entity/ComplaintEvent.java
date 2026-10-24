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
@Table(name = "complaint_events")
@AttributeOverride(name = "id", column = @Column(name = "event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintEvent extends BaseEntity {
    @Column(name = "complaint_id", nullable = false)
    UUID complaintId;
    @Column(name = "actor_id", nullable = false)
    UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    ComplaintAction action;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    ComplaintStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    ComplaintStatus toStatus;
    @Column(name = "note", length = 4000)
    String note;
}
