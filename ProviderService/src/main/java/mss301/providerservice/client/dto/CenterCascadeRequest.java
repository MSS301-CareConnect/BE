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
    Long version;
    String event;
    String reason;
}
