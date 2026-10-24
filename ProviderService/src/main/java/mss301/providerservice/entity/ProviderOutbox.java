package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_outbox", uniqueConstraints = @UniqueConstraint(name = "uk_provider_outbox_event", columnNames = {"center_id", "event_type", "sequence_no"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderOutbox extends BaseEntity {
    @Column(name = "center_id", nullable = false)
    UUID centerId;
    @Column(name = "event_type", nullable = false, length = 30)
    String eventType;
    @Column(name = "sequence_no", nullable = false)
    Long sequenceNo;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    String payload;
    @Column(name = "status", nullable = false, length = 20)
    String status;
    @Column(name = "attempts", nullable = false)
    Integer attempts;
    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;
    @Column(name = "last_error", length = 500)
    String lastError;
}
