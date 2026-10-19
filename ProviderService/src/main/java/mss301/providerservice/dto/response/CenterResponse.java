package mss301.providerservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CenterStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterResponse {
    UUID id;
    String centerName;
    String phoneNumber;
    String email;
    String address;
    String operatingHours;
    CenterStatus centerStatus;
    String statusReason;

    UUID managerUserId;
    UUID createdBy;
    UUID updatedBy;
    Long version;
    Instant createdAt;
    Instant updatedAt;
    Instant deletedAt;

    public UUID getCenterId() {
        return id;
    }
}
