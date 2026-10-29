package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterCascadeRequest {
    UUID centerId;
    UUID managerUserId;
    // H-11: sự kiện CAREGIVER_DEACTIVATED — caregiver_profiles.caregiver_id cần gắn cờ đổi người.
    UUID caregiverId;
    Long version;
    String event;
    String reason;
}
