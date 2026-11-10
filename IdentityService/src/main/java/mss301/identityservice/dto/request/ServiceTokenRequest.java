package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTokenRequest {
    @NotBlank(message = "CLIENT_ID_REQUIRED")
    String clientId;

    @NotBlank(message = "CLIENT_SECRET_REQUIRED")
    @ToString.Exclude
    String clientSecret;
}
