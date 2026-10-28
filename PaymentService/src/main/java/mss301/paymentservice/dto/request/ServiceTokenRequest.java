package mss301.paymentservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTokenRequest {
    String clientId;

    @ToString.Exclude
    String clientSecret;
}
