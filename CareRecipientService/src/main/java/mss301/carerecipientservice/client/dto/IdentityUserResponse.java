package mss301.carerecipientservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

// Bản sao InternalUserResponse của IdentityService (GET/POST /internal/users/**). status/roles giữ dạng String
// để Identity thêm giá trị enum mới không làm hỏng service này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityUserResponse {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    UUID id;

    String email;

    String fullName;

    String phoneNumber;

    String status;

    Set<String> roles;
}
