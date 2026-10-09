package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

// Bản sao InternalUserResponse của IdentityService (POST /internal/users/batch).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityUserResponse {
    UUID id;

    String email;

    String fullName;

    String phoneNumber;

    String status;

    Set<String> roles;
}
