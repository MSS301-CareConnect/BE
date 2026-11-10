package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityUserResponse {
    UUID id;
    String email;
    String fullName;
    String phoneNumber;
    String status;
    Set<String> roles;

    // Trung tâm của CENTER_MANAGER / CAREGIVER theo Identity (claim center_id) — H-12 kiểm khớp với Provider.
    UUID centerId;
}
