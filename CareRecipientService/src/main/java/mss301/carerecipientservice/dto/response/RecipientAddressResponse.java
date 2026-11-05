package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientAddressResponse {
    UUID id;

    String label;

    String addressLine;

    String ward;

    String district;

    String province;

    String note;

    Instant createdAt;

    Instant updatedAt;
}
