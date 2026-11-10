package mss301.providerservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

// H-08: một dòng provider_outbox cho màn hình sự cố admin (không trả payload).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEventResponse {
    UUID id;
    UUID centerId;
    String eventType;
    Long sequenceNo;
    String status;
    Integer attempts;
    Instant nextAttemptAt;
    String lastError;
    Instant createdAt;
    Instant updatedAt;
}
